package com.osir.mcp.telemetry;

import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Buffers MCP session events and batch-posts them to the backend funnel (A.7 → B.6).
 * Fire-and-forget by design: recording never blocks a tool call, a full buffer or a failed
 * POST drops events (telemetry is not a ledger), and everything is off unless an API key
 * (role api-user, issued for the MCP service) is configured, that key is what lets
 * ANONYMOUS sessions be reported too.
 */
@ApplicationScoped
public class TelemetryService {

    private static final Logger LOG = Logger.getLogger(TelemetryService.class);

    private static final int BATCH_SIZE = 200;   // backend maximum
    private static final int MAX_BUFFER = 10_000;

    /** API key for the telemetry endpoint (sent as X-API-Key). Empty/unset = telemetry disabled. */
    @ConfigProperty(name = "osir.telemetry.api-key")
    Optional<String> apiKey;

    @Inject
    @RestClient
    TelemetryClient client;

    private final ConcurrentLinkedQueue<Map<String, Object>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger queued = new AtomicInteger();
    private final AtomicInteger dropped = new AtomicInteger();

    public boolean enabled() {
        return apiKey.filter(k -> !k.isBlank()).isPresent();
    }

    /** Enqueue one event. Never throws, never blocks. Events without a sessionId are dropped by the backend. */
    public void record(Map<String, Object> event) {
        if (!enabled() || event == null) return;
        if (queued.get() >= MAX_BUFFER) {
            dropped.incrementAndGet(); // ponytail: drop-new when full; backend outage must not grow heap
            return;
        }
        queue.add(event);
        queued.incrementAndGet();
    }

    @Scheduled(every = "30s")
    void flush() {
        if (!enabled() || queue.isEmpty()) return;
        int droppedNow = dropped.getAndSet(0);
        if (droppedNow > 0) {
            LOG.warnf("Telemetry buffer overflowed; dropped %d events", droppedNow);
        }
        String key = apiKey.get().trim();
        while (!queue.isEmpty()) {
            List<Map<String, Object>> batch = new ArrayList<>(BATCH_SIZE);
            Map<String, Object> e;
            while (batch.size() < BATCH_SIZE && (e = queue.poll()) != null) {
                queued.decrementAndGet();
                batch.add(e);
            }
            if (batch.isEmpty()) return;
            try {
                client.ingest(key, batch);
            } catch (Exception ex) {
                // Drop the batch: telemetry must never queue forever against a down backend.
                LOG.warnf("Telemetry POST failed, dropped %d events: %s", batch.size(), ex.getMessage());
                return;
            }
        }
    }

    @PreDestroy
    void shutdownFlush() {
        try {
            flush();
        } catch (Exception ignored) {
        }
    }
}
