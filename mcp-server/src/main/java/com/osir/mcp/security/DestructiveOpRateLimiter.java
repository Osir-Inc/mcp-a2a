package com.osir.mcp.security;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-connection rate limiter for MCP tool operations that cost money, destroy data, or publish.
 * Independent buckets per connection, each with a 1-minute sliding window.
 */
@ApplicationScoped
public class DestructiveOpRateLimiter {

    private static final Logger LOG = Logger.getLogger(DestructiveOpRateLimiter.class);

    /**
     * PUBLISH is deliberately looser than the other two: publishing costs nothing and redeploying is
     * a normal part of iterating on a site. It exists because a looping agent can call
     * osirSitePublish every turn with nothing but inline HTML, and the backend throttles nothing.
     */
    public enum Bucket {
        FINANCIAL(5), DESTRUCTIVE(3), PUBLISH(10);

        private final int perMinute;

        Bucket(int perMinute) {
            this.perMinute = perMinute;
        }

        public int perMinute() {
            return perMinute;
        }
    }

    private static final long WINDOW_MS = 60_000;

    private static final class BucketState {
        final AtomicInteger count;
        final long windowStart;
        BucketState(int count, long windowStart) {
            this.count = new AtomicInteger(count);
            this.windowStart = windowStart;
        }
    }

    private final ConcurrentHashMap<String, BucketState> buckets = new ConcurrentHashMap<>();

    public boolean tryAcquire(String connectionId, Bucket bucket) {
        String key = connectionId + ":" + bucket.name();
        long now = System.currentTimeMillis();
        int[] resultCount = {0};

        buckets.compute(key, (k, existing) -> {
            if (existing == null || now - existing.windowStart >= WINDOW_MS) {
                resultCount[0] = 1;
                return new BucketState(1, now);
            }
            resultCount[0] = existing.count.incrementAndGet();
            return existing;
        });

        int limit = bucket.perMinute();
        if (resultCount[0] > limit) {
            LOG.warnf("Rate limit exceeded: conn=%s bucket=%s count=%d limit=%d",
                    connectionId, bucket, resultCount[0], limit);
            return false;
        }
        return true;
    }

    @Scheduled(every = "5m")
    void cleanup() {
        long now = System.currentTimeMillis();
        int removed = 0;
        var it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (now - entry.getValue().windowStart >= WINDOW_MS * 2) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            LOG.debugf("Rate limiter cleanup: removed %d stale buckets", removed);
        }
    }
}
