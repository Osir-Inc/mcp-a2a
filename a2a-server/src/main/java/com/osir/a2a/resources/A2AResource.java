package com.osir.a2a.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.agents.AgentRegistry;
import com.osir.a2a.protocol.*;
import com.osir.mcp.services.AuthContext;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * A2A JSON-RPC 2.0 endpoint.
 * Handles: tasks/send, tasks/get, tasks/cancel
 */
@Path("/a2a")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class A2AResource {

    private static final Logger LOG = Logger.getLogger(A2AResource.class);

    @Inject TaskStore taskStore;
    @Inject AgentRegistry agentRegistry;
    @Inject ObjectMapper objectMapper;
    @Inject AuthContext authContext;
    @Inject AuditLogger auditLogger;
    @Inject PushNotificationService pushService;
    @Inject TokenRefreshService tokenRefreshService;
    @Inject TaskMetrics metrics;

    private static final int MAX_MESSAGE_LENGTH = 10_000;
    private static final int TASK_TIMEOUT_SECONDS = 30;
    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 20, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(100),
            r -> { Thread t = new Thread(r, "a2a-task-" + System.currentTimeMillis()); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @POST
    public JsonRpcResponse handle(JsonRpcRequest request,
                                  @HeaderParam("Authorization") String authorization) {
        // Validate request
        if (request == null) {
            return JsonRpcResponse.error(null, JsonRpcResponse.INVALID_REQUEST, "Empty request");
        }
        if (request.getMethod() == null || request.getMethod().isBlank()) {
            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_REQUEST, "Missing method");
        }
        if (!"2.0".equals(request.getJsonrpc())) {
            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_REQUEST,
                    "Invalid JSON-RPC version");
        }

        // Set request-scoped auth context
        String bearerToken = null;
        if (authorization != null && !authorization.isBlank()) {
            bearerToken = authorization.startsWith("Bearer ") ? authorization : "Bearer " + authorization;
            authContext.setTokenOverride(bearerToken);
        }

        return switch (request.getMethod()) {
            case "tasks/send" -> handleTaskSend(request, bearerToken);
            case "tasks/get" -> handleTaskGet(request);
            case "tasks/cancel" -> handleTaskCancel(request);
            default -> JsonRpcResponse.error(request.getId(), JsonRpcResponse.METHOD_NOT_FOUND,
                    "Unknown method: " + request.getMethod());
        };
    }

    private JsonRpcResponse handleTaskSend(JsonRpcRequest request, String bearerToken) {
        try {
            Map<String, Object> params = request.getParams();
            if (params == null) {
                return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                        "Missing params");
            }

            // Deserialize params into typed DTO
            TaskSendParams sendParams = objectMapper.convertValue(params, TaskSendParams.class);

            if (sendParams.getMessage() == null) {
                return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                        "Missing message in params");
            }

            // Validate message content length
            String textContent = sendParams.getMessage().getTextContent();
            if (textContent != null && textContent.length() > MAX_MESSAGE_LENGTH) {
                return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                        "Message too long (max " + MAX_MESSAGE_LENGTH + " chars)");
            }

            // Token lifecycle: check expiry, attempt refresh if needed
            String refreshedAccessToken = null;
            String refreshedRefreshToken = null;
            if (bearerToken != null) {
                if (tokenRefreshService.isExpired(bearerToken)) {
                    // Token is expired — try to refresh if refresh_token provided
                    if (sendParams.getRefreshToken() != null) {
                        var refreshResult = tokenRefreshService.refresh(sendParams.getRefreshToken());
                        if (refreshResult != null) {
                            authContext.setTokenOverride(refreshResult.accessToken());
                            refreshedAccessToken = refreshResult.accessToken();
                            refreshedRefreshToken = refreshResult.refreshToken();
                            metrics.tokenRefresh(true);
                        } else {
                            metrics.tokenRefresh(false);
                            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.TOKEN_EXPIRED,
                                    "Token expired and refresh failed. Please re-authenticate.");
                        }
                    } else {
                        return JsonRpcResponse.error(request.getId(), JsonRpcResponse.TOKEN_EXPIRED,
                                "Token expired. Provide a refreshToken in params or re-authenticate.");
                    }
                }
            }

            String taskId = sendParams.getId() != null ? sendParams.getId() : UUID.randomUUID().toString();

            // Build task metadata from explicit routing params
            Map<String, Object> metadata = sendParams.getMetadata() != null
                    ? new HashMap<>(sendParams.getMetadata()) : new HashMap<>();
            if (sendParams.getSkill() != null) metadata.put("skill", sendParams.getSkill());
            if (sendParams.getAgent() != null) metadata.put("agent", sendParams.getAgent());

            // Register webhook for push notifications
            if (sendParams.getWebhookUrl() != null) {
                pushService.register(taskId, sendParams.getWebhookUrl());
            }

            // Check if this is a continuation of an existing task
            A2ATask task = taskStore.get(taskId).orElse(null);

            if (task != null) {
                task.addMessage(sendParams.getMessage());
                if (!metadata.isEmpty()) task.setMetadata(metadata);
            } else {
                task = taskStore.create(taskId, sendParams.getMessage());
                if (!metadata.isEmpty()) task.setMetadata(metadata);
                metrics.taskCreated();
            }

            // Route to the best specialist agent
            var agent = agentRegistry.findAgentForTask(task);
            if (agent.isEmpty()) {
                task.transitionTo(TaskState.FAILED);
                task.addMessage(new Message("agent", "No agent available to handle this request."));
                auditLogger.logTaskFailed(taskId, null, "No agent available");
                metrics.taskFailed();
                return JsonRpcResponse.success(request.getId(), task);
            }

            // Resolve agent once
            final var agentRef = agent.get();

            // Audit: log task submission
            String user = authContext.hasOverride() ? "authenticated" : "anonymous";
            auditLogger.logTaskSubmitted(taskId, user, sendParams.getSkill(),
                    agentRef.getId(), textContent);

            // Execute with timeout
            long startTime = System.currentTimeMillis();
            task.transitionTo(TaskState.WORKING);

            final A2ATask taskRef = task;
            try {
                task = executor.submit(() -> agentRef.handle(taskRef))
                        .get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                taskRef.transitionTo(TaskState.FAILED);
                taskRef.addMessage(new Message("agent", "Task timed out after " + TASK_TIMEOUT_SECONDS + " seconds."));
                task = taskRef;
                auditLogger.logTaskFailed(taskId, agentRef.getId(), "Timeout");
                metrics.taskTimedOut();
            } catch (ExecutionException e) {
                taskRef.transitionTo(TaskState.FAILED);
                taskRef.addMessage(new Message("agent", "Task execution failed."));
                task = taskRef;
                LOG.errorf(e.getCause(), "Agent execution error: %s", e.getCause().getMessage());
                auditLogger.logTaskFailed(taskId, agentRef.getId(), "Execution error");
                metrics.taskFailed();
            }
            taskStore.update(task);

            // Metrics + audit: log completion
            long duration = Math.max(0, System.currentTimeMillis() - startTime);
            metrics.recordDuration(duration);
            if (task.getStatus() == TaskState.COMPLETED) {
                auditLogger.logTaskCompleted(taskId, agentRef.getId(), task.getStatus().getValue(), duration);
                metrics.taskCompleted();
            }

            // Push notification if webhook registered
            pushService.notifyIfRegistered(task);

            // Build response — include token info if refreshed or near-expiry
            JsonRpcResponse response = JsonRpcResponse.success(request.getId(), task);
            if (refreshedAccessToken != null) {
                // Token was refreshed — include new tokens in response
                Map<String, Object> tokenInfo = new java.util.LinkedHashMap<>();
                tokenInfo.put("accessToken", refreshedAccessToken);
                if (refreshedRefreshToken != null) tokenInfo.put("refreshToken", refreshedRefreshToken);
                task.setMetadata(task.getMetadata() != null
                        ? new HashMap<>(task.getMetadata()) {{ put("token", tokenInfo); }}
                        : Map.of("token", tokenInfo));
                response = JsonRpcResponse.success(request.getId(), task);
            } else if (bearerToken != null && tokenRefreshService.isNearExpiry(bearerToken)) {
                // Token is near expiry — warn the client
                long remaining = tokenRefreshService.getSecondsUntilExpiry(bearerToken);
                Map<String, Object> meta = task.getMetadata() != null
                        ? new HashMap<>(task.getMetadata()) : new HashMap<>();
                meta.put("tokenExpiresIn", remaining);
                task.setMetadata(meta);
                response = JsonRpcResponse.success(request.getId(), task);
            }
            return response;

        } catch (Exception e) {
            LOG.errorf(e, "Error handling tasks/send: %s", e.getMessage());
            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INTERNAL_ERROR,
                    "An internal error occurred. Please try again or contact support.");
        }
    }

    private JsonRpcResponse handleTaskGet(JsonRpcRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("id")) {
            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                    "Missing task id");
        }

        String taskId = (String) params.get("id");
        return taskStore.get(taskId)
                .map(task -> JsonRpcResponse.success(request.getId(), task))
                .orElse(JsonRpcResponse.error(request.getId(), JsonRpcResponse.TASK_NOT_FOUND,
                        "Task not found: " + taskId));
    }

    private JsonRpcResponse handleTaskCancel(JsonRpcRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("id")) {
            return JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                    "Missing task id");
        }

        String taskId = (String) params.get("id");
        if (taskStore.cancel(taskId)) {
            return JsonRpcResponse.success(request.getId(),
                    taskStore.get(taskId).orElse(null));
        }

        return JsonRpcResponse.error(request.getId(), JsonRpcResponse.TASK_NOT_CANCELABLE,
                "Task cannot be canceled (not found or already terminal)");
    }
}
