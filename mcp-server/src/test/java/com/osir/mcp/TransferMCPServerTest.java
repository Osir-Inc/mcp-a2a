package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.transfer.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.TransferService;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TransferMCPServerTest {

    @Mock
    TransferService transferService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    TransferMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    // ===== getTransferQuote =====

    @Test
    void getTransferQuote_delegatesToService() {
        TransferQuoteResult expected = new TransferQuoteResult(true, "OK");
        when(transferService.getQuote("example.com")).thenReturn(expected);

        TransferQuoteResult result = mcpServer.getTransferQuote("example.com", mockConnection);

        assertSame(expected, result);
        verify(transferService).getQuote("example.com");
    }

    @Test
    void getTransferQuote_handlesException() {
        when(transferService.getQuote("example.com")).thenThrow(new RuntimeException("Fail"));

        TransferQuoteResult result = mcpServer.getTransferQuote("example.com", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== initiateTransfer =====

    @Test
    void initiateTransfer_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "initiateTransfer", "summary");
        when(pendingActionStore.stage(eq("initiateTransfer"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.initiateTransfer("example.com", "EPP-123", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("initiateTransfer"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any());
    }

    // ===== getTransferStatus =====

    @Test
    void getTransferStatus_delegatesToService() {
        TransferStatusResult expected = new TransferStatusResult(true, "OK");
        when(transferService.getStatus("example.com")).thenReturn(expected);

        TransferStatusResult result = mcpServer.getTransferStatus("example.com", mockConnection);

        assertSame(expected, result);
        verify(transferService).getStatus("example.com");
    }

    @Test
    void getTransferStatus_handlesException() {
        when(transferService.getStatus("example.com")).thenThrow(new RuntimeException("Fail"));

        TransferStatusResult result = mcpServer.getTransferStatus("example.com", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== cancelTransfer =====

    @Test
    void cancelTransfer_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "cancelTransfer", "summary");
        when(pendingActionStore.stage(eq("cancelTransfer"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.cancelTransfer("example.com", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("cancelTransfer"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    // ===== listPendingTransfers =====

    @Test
    void listPendingTransfers_delegatesToService() {
        PendingTransferListResult expected = new PendingTransferListResult(true, "OK");
        when(transferService.listPending()).thenReturn(expected);

        PendingTransferListResult result = mcpServer.listPendingTransfers(mockConnection);

        assertSame(expected, result);
        verify(transferService).listPending();
    }

    @Test
    void listPendingTransfers_handlesException() {
        when(transferService.listPending()).thenThrow(new RuntimeException("Fail"));

        PendingTransferListResult result = mcpServer.listPendingTransfers(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
