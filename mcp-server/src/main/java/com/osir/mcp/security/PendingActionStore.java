package com.osir.mcp.security;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class PendingActionStore {

    private static final Logger LOG = Logger.getLogger(PendingActionStore.class);
    private static final long TTL_MS = 300_000; // 5 minutes

    private final ConcurrentHashMap<String, PendingAction> store = new ConcurrentHashMap<>();

    public ConfirmationRequiredResult stage(
            String toolName,
            String summary,
            String connectionId,
            DestructiveOpRateLimiter.Bucket bucket,
            Callable<Object> action) {

        String actionId = UUID.randomUUID().toString();
        long expiresAt = System.currentTimeMillis() + TTL_MS;
        store.put(actionId, new PendingAction(actionId, toolName, summary, connectionId, bucket, expiresAt, action));
        LOG.debugf("Staged action: id=%s tool=%s conn=%s", actionId, toolName, connectionId);
        ConfirmationRequiredResult result = new ConfirmationRequiredResult(actionId, toolName, summary);
        result.setExpiresIn((TTL_MS / 60_000) + " minutes");
        return result;
    }

    /**
     * Atomically claims the action for execution. Returns empty if not found or already claimed.
     * The caller is responsible for checking expiry and connection match after claim.
     */
    public Optional<PendingAction> claim(String actionId) {
        PendingAction action = store.remove(actionId);
        return Optional.ofNullable(action);
    }

    @Scheduled(every = "1m")
    void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var it = store.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getValue().expiresAt() < now) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOG.debugf("Pending action cleanup: removed %d expired entries", removed);
        }
    }
}
