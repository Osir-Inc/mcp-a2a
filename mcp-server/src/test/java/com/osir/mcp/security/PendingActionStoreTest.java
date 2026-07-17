package com.osir.mcp.security;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PendingActionStoreTest {

    private PendingActionStore store;

    @BeforeEach
    void setUp() {
        store = new PendingActionStore();
    }

    // ===== stage() =====

    @Test
    void stage_returnsResultWithCorrectFields() {
        ConfirmationRequiredResult result = store.stage(
                "deleteContact", "Delete contact 'c-1'", "conn-1",
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);

        assertNotNull(result.getActionId());
        assertEquals("deleteContact", result.getToolName());
        assertEquals("Delete contact 'c-1'", result.getSummary());
        assertNotNull(result.getInstruction());
        assertTrue(result.getInstruction().contains("executeConfirmedAction"));
    }

    @Test
    void stage_expiresIn_derivedFromTtl() {
        ConfirmationRequiredResult result = store.stage(
                "tool", "summary", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);

        assertNotNull(result.getExpiresIn());
        assertTrue(result.getExpiresIn().contains("minutes"),
                "expiresIn should describe a duration in minutes, got: " + result.getExpiresIn());
    }

    @Test
    void stage_eachCallProducesUniqueActionId() {
        String id1 = store.stage("tool", "s", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null).getActionId();
        String id2 = store.stage("tool", "s", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null).getActionId();
        assertNotEquals(id1, id2);
    }

    @Test
    void stage_expiresAt_isApproximatelyFiveMinutesFromNow() {
        long before = System.currentTimeMillis();
        ConfirmationRequiredResult staged = store.stage(
                "tool", "summary", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);
        long after = System.currentTimeMillis();

        Optional<PendingAction> action = store.claim(staged.getActionId());
        assertTrue(action.isPresent());
        long expiresAt = action.get().expiresAt();
        assertTrue(expiresAt >= before + 300_000, "expiresAt should be at least 5 min from before");
        assertTrue(expiresAt <= after + 300_000, "expiresAt should be at most 5 min from after");
    }

    // ===== claim() =====

    @Test
    void claim_returnsActionAfterStage() throws Exception {
        ConfirmationRequiredResult staged = store.stage(
                "orderVps", "Order VPS", "conn-1",
                DestructiveOpRateLimiter.Bucket.FINANCIAL, () -> "vps-result");

        Optional<PendingAction> claimed = store.claim(staged.getActionId());

        assertTrue(claimed.isPresent());
        assertEquals("orderVps", claimed.get().toolName());
        assertEquals("Order VPS", claimed.get().summary());
        assertEquals("conn-1", claimed.get().connectionId());
        assertEquals(DestructiveOpRateLimiter.Bucket.FINANCIAL, claimed.get().bucket());
        assertEquals("vps-result", claimed.get().action().call());
    }

    @Test
    void claim_missingId_returnsEmpty() {
        Optional<PendingAction> result = store.claim("non-existent-uuid");
        assertTrue(result.isEmpty());
    }

    @Test
    void claim_calledTwice_secondReturnsEmpty() {
        ConfirmationRequiredResult staged = store.stage(
                "tool", "summary", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);
        String actionId = staged.getActionId();

        Optional<PendingAction> first = store.claim(actionId);
        Optional<PendingAction> second = store.claim(actionId);

        assertTrue(first.isPresent());
        assertTrue(second.isEmpty());
    }

    @Test
    void claim_concurrent_onlyOneSucceeds() throws Exception {
        ConfirmationRequiredResult staged = store.stage(
                "tool", "summary", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);
        String actionId = staged.getActionId();

        int threads = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        var executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    if (store.claim(actionId).isPresent()) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        executor.shutdown();

        assertEquals(1, successCount.get(), "Exactly one thread should claim the action");
    }

    // ===== cleanup() =====

    @Test
    void cleanup_removesExpiredEntries_keepsActive() throws Exception {
        ConfirmationRequiredResult active = store.stage(
                "active", "summary", "conn", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE, () -> null);

        // inject an already-expired entry directly into the internal map
        var field = PendingActionStore.class.getDeclaredField("store");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var internalStore = (ConcurrentHashMap<String, PendingAction>) field.get(store);

        PendingAction expired = new PendingAction(
                "expired-id", "expired", "summary", "conn",
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() - 1,
                () -> null);
        internalStore.put("expired-id", expired);

        store.cleanup();

        assertTrue(store.claim(active.getActionId()).isPresent(), "Active entry should survive cleanup");
        assertTrue(store.claim("expired-id").isEmpty(), "Expired entry should be removed by cleanup");
    }

    @Test
    void cleanup_noEntries_doesNotThrow() {
        assertDoesNotThrow(() -> store.cleanup());
    }
}
