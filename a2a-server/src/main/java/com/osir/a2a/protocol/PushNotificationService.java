package com.osir.a2a.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Push notification service for A2A tasks.
 * Clients can register a webhook URL when creating a task.
 * When the task completes/fails, a POST is sent to the webhook with the task result.
 */
@ApplicationScoped
public class PushNotificationService {

    private static final Logger LOG = Logger.getLogger(PushNotificationService.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Map<String, String> webhooks = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    @Inject ObjectMapper objectMapper;

    /**
     * Register a webhook URL for push notifications on a task.
     */
    public void register(String taskId, String webhookUrl) {
        if (webhookUrl != null && !webhookUrl.isBlank()) {
            webhooks.put(taskId, webhookUrl);
            LOG.infof("Push notification registered for task %s -> %s", taskId, webhookUrl);
        }
    }

    /**
     * Send a push notification for a completed/failed task.
     * Called after task execution completes.
     */
    public void notifyIfRegistered(A2ATask task) {
        String webhookUrl = webhooks.remove(task.getId());
        if (webhookUrl == null) return;
        if (!task.isTerminal()) return;

        sendWithRetry(webhookUrl, task, 0);
    }

    private void sendWithRetry(String url, A2ATask task, int attempt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "taskId", task.getId(),
                    "status", task.getStatus().getValue(),
                    "artifacts", task.getArtifacts()
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            for (int i = attempt; i <= MAX_RETRIES; i++) {
                try {
                    if (i > attempt) {
                        long backoffMs = Math.min(1000L * (1L << (i - 1)), 10_000L);
                        LOG.debugf("Push retry %d/%d for task %s in %dms", i, MAX_RETRIES, task.getId(), backoffMs);
                        Thread.sleep(backoffMs);
                    }

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        LOG.infof("Push notification sent for task %s (status=%d)", task.getId(), response.statusCode());
                        return;
                    }
                    LOG.warnf("Push notification failed for task %s (status=%d, attempt %d/%d)",
                            task.getId(), response.statusCode(), i + 1, MAX_RETRIES);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.warnf("Push notification interrupted for task %s", task.getId());
                    return;
                } catch (Exception e) {
                    LOG.warnf("Push notification error for task %s (attempt %d/%d): %s",
                            task.getId(), i + 1, MAX_RETRIES, e.getMessage());
                }
            }
            LOG.errorf("Push notification permanently failed for task %s after %d retries", task.getId(), MAX_RETRIES);
        } catch (Exception e) {
            LOG.errorf("Push notification setup failed for task %s: %s", task.getId(), e.getMessage());
        }
    }
}
