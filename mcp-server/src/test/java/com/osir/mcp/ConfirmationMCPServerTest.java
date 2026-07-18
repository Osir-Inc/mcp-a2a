package com.osir.mcp;

import com.osir.mcp.models.confirmation.ActionExecutionResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingAction;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConfirmationMCPServerTest {

    private static final String PRINCIPAL = "user:alice";

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    DestructiveOpRateLimiter rateLimiter;

    @Mock
    McpAuthHelper authHelper;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    ConfirmationMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
        when(authHelper.currentPrincipal(mockConnection)).thenReturn(PRINCIPAL);
        when(rateLimiter.tryAcquire(any(), any())).thenReturn(true);
    }

    private static PendingAction action(String id, String tool, String owner,
                                        DestructiveOpRateLimiter.Bucket bucket, long expiresAt,
                                        java.util.concurrent.Callable<Object> call) {
        return new PendingAction(id, tool, tool, owner, "any-conn", bucket, expiresAt, call);
    }

    @Test
    void executeConfirmedAction_executesSuccessfully() {
        Object serviceResult = new Object();
        PendingAction pending = action("action-1", "deleteContact", PRINCIPAL,
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() + 300_000, () -> serviceResult);
        when(pendingActionStore.claim("action-1")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-1", mockConnection);

        assertTrue(result.isSuccess());
        assertSame(serviceResult, result.getResult());
        // Rate limiting keys on the user principal, not the connection id.
        verify(rateLimiter).tryAcquire(PRINCIPAL, DestructiveOpRateLimiter.Bucket.DESTRUCTIVE);
    }

    @Test
    void executeConfirmedAction_notFound_returnsError() {
        when(pendingActionStore.claim("missing-id")).thenReturn(Optional.empty());

        ActionExecutionResult result = mcpServer.executeConfirmedAction("missing-id", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not found"));
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    void executeConfirmedAction_expired_returnsError() {
        PendingAction expired = action("action-exp", "deleteDnsRecord", PRINCIPAL,
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() - 1, () -> null);
        when(pendingActionStore.claim("action-exp")).thenReturn(Optional.of(expired));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-exp", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("expired"));
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    void executeConfirmedAction_wrongOwner_returnsError() {
        // Staged by a different user; the caller's principal is alice — must be rejected even
        // though (unlike before) it may arrive on the same or a different MCP connection.
        PendingAction pending = action("action-2", "orderVps", "user:bob",
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                System.currentTimeMillis() + 300_000, () -> null);
        when(pendingActionStore.claim("action-2")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-2", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("different user"));
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    void executeConfirmedAction_sameUserDifferentConnection_executes() {
        // The regression this whole change fixes: staged on one MCP connection, executed on
        // another, but the SAME authenticated user — must succeed.
        PendingAction pending = action("action-x", "registerDomain", PRINCIPAL,
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                System.currentTimeMillis() + 300_000, () -> "ok");
        when(pendingActionStore.claim("action-x")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-x", mockConnection);

        assertTrue(result.isSuccess());
        verify(rateLimiter).tryAcquire(PRINCIPAL, DestructiveOpRateLimiter.Bucket.FINANCIAL);
    }

    @Test
    void executeConfirmedAction_rateLimitExceeded_returnsError() {
        PendingAction pending = action("action-3", "payInvoice", PRINCIPAL,
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                System.currentTimeMillis() + 300_000, () -> null);
        when(pendingActionStore.claim("action-3")).thenReturn(Optional.of(pending));
        when(rateLimiter.tryAcquire(eq(PRINCIPAL), eq(DestructiveOpRateLimiter.Bucket.FINANCIAL))).thenReturn(false);

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-3", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Rate limit"));
    }

    @Test
    void executeConfirmedAction_serviceThrows_returnsFailure() {
        PendingAction pending = action("action-4", "deleteHost", PRINCIPAL,
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() + 300_000,
                () -> { throw new RuntimeException("Backend error"); });
        when(pendingActionStore.claim("action-4")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-4", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Backend error"));
    }

    @Test
    void executeConfirmedAction_atomicClaim_preventsDoubleExecution() {
        when(pendingActionStore.claim("action-5")).thenReturn(Optional.empty());

        ActionExecutionResult first = mcpServer.executeConfirmedAction("action-5", mockConnection);
        ActionExecutionResult second = mcpServer.executeConfirmedAction("action-5", mockConnection);

        assertFalse(first.isSuccess());
        assertFalse(second.isSuccess());
        verify(pendingActionStore, times(2)).claim("action-5");
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }
}
