package com.osir.a2a.resources;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Prometheus metrics for A2A task operations.
 * Exposed at /q/metrics for scraping.
 */
@ApplicationScoped
public class TaskMetrics {

    private final Counter tasksCreated;
    private final Counter tasksCompleted;
    private final Counter tasksFailed;
    private final Counter tasksTimedOut;
    private final Counter tokenRefreshes;
    private final Counter tokenRefreshFailures;
    private final Counter webhooksSent;
    private final Counter webhooksFailed;
    private final Timer taskDuration;

    @Inject
    public TaskMetrics(MeterRegistry registry) {
        tasksCreated = Counter.builder("a2a.tasks.created")
                .description("Total A2A tasks created")
                .register(registry);
        tasksCompleted = Counter.builder("a2a.tasks.completed")
                .description("Total A2A tasks completed successfully")
                .register(registry);
        tasksFailed = Counter.builder("a2a.tasks.failed")
                .description("Total A2A tasks failed")
                .register(registry);
        tasksTimedOut = Counter.builder("a2a.tasks.timed_out")
                .description("Total A2A tasks that timed out")
                .register(registry);
        tokenRefreshes = Counter.builder("a2a.token.refreshes")
                .description("Total token refresh attempts")
                .register(registry);
        tokenRefreshFailures = Counter.builder("a2a.token.refresh_failures")
                .description("Total token refresh failures")
                .register(registry);
        webhooksSent = Counter.builder("a2a.webhooks.sent")
                .description("Total webhook notifications sent")
                .register(registry);
        webhooksFailed = Counter.builder("a2a.webhooks.failed")
                .description("Total webhook notification failures")
                .register(registry);
        taskDuration = Timer.builder("a2a.tasks.duration")
                .description("Task execution duration")
                .register(registry);
    }

    public void taskCreated() { tasksCreated.increment(); }
    public void taskCompleted() { tasksCompleted.increment(); }
    public void taskFailed() { tasksFailed.increment(); }
    public void taskTimedOut() { tasksTimedOut.increment(); }
    public void tokenRefresh(boolean success) {
        if (success) tokenRefreshes.increment();
        else tokenRefreshFailures.increment();
    }
    public void webhookSent() { webhooksSent.increment(); }
    public void webhookFailed() { webhooksFailed.increment(); }
    public void recordDuration(long millis) { taskDuration.record(Duration.ofMillis(millis)); }
}
