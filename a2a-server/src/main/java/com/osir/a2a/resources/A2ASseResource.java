package com.osir.a2a.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.agents.AgentRegistry;
import com.osir.a2a.agents.SpecialistAgent;
import com.osir.a2a.protocol.*;
import com.osir.mcp.services.AuthContext;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * A2A SSE streaming endpoint for tasks/sendSubscribe.
 * Streams task state updates as SSE events during execution.
 * Mirrors A2AResource fixes: timeout, audit, push, bounded execution.
 */
@Path("/a2a/stream")
public class A2ASseResource {

    private static final Logger LOG = Logger.getLogger(A2ASseResource.class);
    private static final int TASK_TIMEOUT_SECONDS = 30;
    private static final int MAX_MESSAGE_LENGTH = 10_000;

    @Inject TaskStore taskStore;
    @Inject AgentRegistry agentRegistry;
    @Inject ObjectMapper objectMapper;
    @Inject AuthContext authContext;
    @Inject AuditLogger auditLogger;
    @Inject PushNotificationService pushService;

    private final ExecutorService executor = new ThreadPoolExecutor(
            2, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(50),
            r -> { Thread t = new Thread(r, "a2a-sse-" + System.currentTimeMillis()); t.setDaemon(true); return t; },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<JsonRpcResponse> sendSubscribe(JsonRpcRequest request,
                                                @HeaderParam("Authorization") String authorization) {
        // Set auth context
        if (authorization != null && !authorization.isBlank()) {
            String token = authorization.startsWith("Bearer ") ? authorization : "Bearer " + authorization;
            authContext.setTokenOverride(token);
        }

        return Multi.createFrom().emitter(emitter -> {
            try {
                Map<String, Object> params = request.getParams();
                if (params == null) {
                    emitter.emit(JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS, "Missing params"));
                    emitter.complete();
                    return;
                }

                TaskSendParams sendParams = objectMapper.convertValue(params, TaskSendParams.class);
                if (sendParams.getMessage() == null) {
                    emitter.emit(JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS, "Missing message"));
                    emitter.complete();
                    return;
                }

                // Validate message length
                String textContent = sendParams.getMessage().getTextContent();
                if (textContent != null && textContent.length() > MAX_MESSAGE_LENGTH) {
                    emitter.emit(JsonRpcResponse.error(request.getId(), JsonRpcResponse.INVALID_PARAMS,
                            "Message too long (max " + MAX_MESSAGE_LENGTH + " chars)"));
                    emitter.complete();
                    return;
                }

                String taskId = sendParams.getId() != null ? sendParams.getId() : UUID.randomUUID().toString();

                Map<String, Object> metadata = sendParams.getMetadata() != null
                        ? new HashMap<>(sendParams.getMetadata()) : new HashMap<>();
                if (sendParams.getSkill() != null) metadata.put("skill", sendParams.getSkill());
                if (sendParams.getAgent() != null) metadata.put("agent", sendParams.getAgent());

                // Register webhook
                if (sendParams.getWebhookUrl() != null) {
                    pushService.register(taskId, sendParams.getWebhookUrl());
                }

                A2ATask task = taskStore.get(taskId).orElse(null);
                if (task != null) {
                    task.addMessage(sendParams.getMessage());
                    if (!metadata.isEmpty()) task.setMetadata(metadata);
                } else {
                    task = taskStore.create(taskId, sendParams.getMessage());
                    if (!metadata.isEmpty()) task.setMetadata(metadata);
                }

                // Emit "submitted" state
                emitter.emit(JsonRpcResponse.success(request.getId(), task));

                // Route
                var agent = agentRegistry.findAgentForTask(task);
                if (agent.isEmpty()) {
                    task.transitionTo(TaskState.FAILED);
                    task.addMessage(new Message("agent", "No agent available to handle this request."));
                    auditLogger.logTaskFailed(taskId, null, "No agent available");
                    emitter.emit(JsonRpcResponse.success(request.getId(), task));
                    emitter.complete();
                    return;
                }

                final SpecialistAgent agentRef = agent.get();
                String user = authContext.hasOverride() ? "authenticated" : "anonymous";
                auditLogger.logTaskSubmitted(taskId, user, sendParams.getSkill(), agentRef.getId(), textContent);

                // Emit "working" state
                long startTime = System.currentTimeMillis();
                task.transitionTo(TaskState.WORKING);
                emitter.emit(JsonRpcResponse.success(request.getId(), task));

                // Execute with timeout
                final A2ATask taskRef = task;
                try {
                    task = executor.submit(() -> agentRef.handle(taskRef))
                            .get(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    taskRef.transitionTo(TaskState.FAILED);
                    taskRef.addMessage(new Message("agent", "Task timed out after " + TASK_TIMEOUT_SECONDS + " seconds."));
                    task = taskRef;
                    auditLogger.logTaskFailed(taskId, agentRef.getId(), "Timeout");
                } catch (ExecutionException e) {
                    taskRef.transitionTo(TaskState.FAILED);
                    taskRef.addMessage(new Message("agent", "Task execution failed."));
                    task = taskRef;
                    LOG.errorf(e.getCause(), "SSE agent error: %s", e.getCause().getMessage());
                    auditLogger.logTaskFailed(taskId, agentRef.getId(), "Execution error");
                }
                taskStore.update(task);

                // Audit
                long duration = Math.max(0, System.currentTimeMillis() - startTime);
                if (task.getStatus() == TaskState.COMPLETED) {
                    auditLogger.logTaskCompleted(taskId, agentRef.getId(), task.getStatus().getValue(), duration);
                }

                // Push notification
                pushService.notifyIfRegistered(task);

                // Emit final state
                emitter.emit(JsonRpcResponse.success(request.getId(), task));
                emitter.complete();

            } catch (Exception e) {
                LOG.errorf(e, "SSE stream error: %s", e.getMessage());
                emitter.emit(JsonRpcResponse.error(request.getId(), JsonRpcResponse.INTERNAL_ERROR,
                        "An internal error occurred."));
                emitter.complete();
            }
        });
    }
}
