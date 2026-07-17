package com.osir.mcp;

import com.osir.mcp.models.confirmation.ActionExecutionResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingAction;
import com.osir.mcp.security.PendingActionStore;
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

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    DestructiveOpRateLimiter rateLimiter;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    ConfirmationMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
        when(rateLimiter.tryAcquire(any(), any())).thenReturn(true);
    }

    @Test
    void executeConfirmedAction_executesSuccessfully() throws Exception {
        Object serviceResult = new Object();
        PendingAction pending = new PendingAction(
                "action-1", "deleteContact", "Delete contact 'c-1'",
                "test-conn-id", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() + 300_000,
                () -> serviceResult
        );
        when(pendingActionStore.claim("action-1")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-1", mockConnection);

        assertTrue(result.isSuccess());
        assertSame(serviceResult, result.getResult());
        verify(rateLimiter).tryAcquire("test-conn-id", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE);
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
        PendingAction expired = new PendingAction(
                "action-exp", "deleteDnsRecord", "Delete record",
                "test-conn-id", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() - 1,
                () -> null
        );
        when(pendingActionStore.claim("action-exp")).thenReturn(Optional.of(expired));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-exp", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("expired"));
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    void executeConfirmedAction_wrongConnection_returnsError() {
        PendingAction pending = new PendingAction(
                "action-2", "orderVps", "Order VPS",
                "other-conn-id", DestructiveOpRateLimiter.Bucket.FINANCIAL,
                System.currentTimeMillis() + 300_000,
                () -> null
        );
        when(pendingActionStore.claim("action-2")).thenReturn(Optional.of(pending));

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-2", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("different session"));
        verify(rateLimiter, never()).tryAcquire(any(), any());
    }

    @Test
    void executeConfirmedAction_rateLimitExceeded_returnsError() {
        PendingAction pending = new PendingAction(
                "action-3", "payInvoice", "Pay invoice",
                "test-conn-id", DestructiveOpRateLimiter.Bucket.FINANCIAL,
                System.currentTimeMillis() + 300_000,
                () -> null
        );
        when(pendingActionStore.claim("action-3")).thenReturn(Optional.of(pending));
        when(rateLimiter.tryAcquire(eq("test-conn-id"), eq(DestructiveOpRateLimiter.Bucket.FINANCIAL))).thenReturn(false);

        ActionExecutionResult result = mcpServer.executeConfirmedAction("action-3", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Rate limit"));
    }

    @Test
    void executeConfirmedAction_serviceThrows_returnsFailure() throws Exception {
        PendingAction pending = new PendingAction(
                "action-4", "deleteHost", "Delete host",
                "test-conn-id", DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                System.currentTimeMillis() + 300_000,
                () -> { throw new RuntimeException("Backend error"); }
        );
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
