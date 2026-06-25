package com.osir.mcp.services;

import com.osir.mcp.clients.TransferBackendClient;
import com.osir.mcp.models.transfer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TransferServiceTest {

    @Mock
    TransferBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    TransferService transferService;

    private static final String TEST_TOKEN = "Bearer test-token";
    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_AUTH_CODE = "EPP-AUTH-12345";
    private static final String TEST_TRANSFER_ID = "xfer-001";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== getQuote =====

    @Test
    void getQuote_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        TransferQuoteResponse response = new TransferQuoteResponse();
        response.setDomain(TEST_DOMAIN);
        response.setTransferPrice("12.99");
        response.setCurrency("USD");
        response.setExtensionYears("1");
        response.setNewExpirationDate("2027-02-19");
        when(backendClient.getTransferQuote(TEST_DOMAIN, TEST_TOKEN)).thenReturn(response);

        TransferQuoteResult result = transferService.getQuote(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("12.99", result.getTransferPrice());
        assertEquals("USD", result.getCurrency());
        assertEquals("1", result.getExtensionYears());
        assertEquals("2027-02-19", result.getNewExpirationDate());
    }

    @Test
    void getQuote_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        TransferQuoteResult result = transferService.getQuote(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void getQuote_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getTransferQuote(TEST_DOMAIN, TEST_TOKEN)).thenThrow(new RuntimeException("Timeout"));

        TransferQuoteResult result = transferService.getQuote(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
    }

    // ===== initiateTransfer =====

    @Test
    void initiateTransfer_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        TransferInitiateResponse response = new TransferInitiateResponse();
        response.setSuccess(true);
        response.setDomain(TEST_DOMAIN);
        response.setTransferId(TEST_TRANSFER_ID);
        response.setStatus("pending");
        when(backendClient.initiateTransfer(any(TransferInitiateRequest.class), eq(TEST_TOKEN)))
                .thenReturn(response);

        TransferInitiateResult result = transferService.initiateTransfer(TEST_DOMAIN, TEST_AUTH_CODE);

        assertTrue(result.isSuccess());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals(TEST_TRANSFER_ID, result.getTransferId());
        assertEquals("pending", result.getStatus());
    }

    @Test
    void initiateTransfer_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        TransferInitiateResult result = transferService.initiateTransfer(TEST_DOMAIN, TEST_AUTH_CODE);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void initiateTransfer_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.initiateTransfer(any(TransferInitiateRequest.class), eq(TEST_TOKEN)))
                .thenThrow(new RuntimeException("Connection refused"));

        TransferInitiateResult result = transferService.initiateTransfer(TEST_DOMAIN, TEST_AUTH_CODE);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    // ===== getStatus =====

    @Test
    void getStatus_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        TransferStatusResponse response = new TransferStatusResponse();
        response.setDomain(TEST_DOMAIN);
        response.setStatus("pending_approval");
        response.setRequestDate("2026-02-19");
        response.setCurrentRegistrar("OtherRegistrar Inc.");
        response.setExpectedCompletion("2026-02-26");
        when(backendClient.getTransferStatus(TEST_DOMAIN, TEST_TOKEN)).thenReturn(response);

        TransferStatusResult result = transferService.getStatus(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("pending_approval", result.getStatus());
        assertEquals("2026-02-19", result.getRequestDate());
        assertEquals("OtherRegistrar Inc.", result.getCurrentRegistrar());
        assertEquals("2026-02-26", result.getExpectedCompletion());
    }

    @Test
    void getStatus_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        TransferStatusResult result = transferService.getStatus(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void getStatus_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getTransferStatus(TEST_DOMAIN, TEST_TOKEN)).thenThrow(new RuntimeException("Not found"));

        TransferStatusResult result = transferService.getStatus(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Not found"));
    }

    // ===== cancelTransfer =====

    @Test
    void cancelTransfer_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        TransferActionResponse response = new TransferActionResponse();
        response.setSuccess(true);
        when(backendClient.cancelTransfer(TEST_DOMAIN, TEST_TOKEN)).thenReturn(response);

        TransferActionResult result = transferService.cancelTransfer(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("cancelled successfully"));
    }

    @Test
    void cancelTransfer_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        TransferActionResult result = transferService.cancelTransfer(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void cancelTransfer_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.cancelTransfer(TEST_DOMAIN, TEST_TOKEN)).thenThrow(new RuntimeException("Server error"));

        TransferActionResult result = transferService.cancelTransfer(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Server error"));
    }

    // ===== listPending =====

    @Test
    void listPending_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        PendingTransfer transfer = new PendingTransfer();
        transfer.setDomain(TEST_DOMAIN);
        transfer.setStatus("pending_approval");
        transfer.setRequestDate("2026-02-19");
        transfer.setCurrentRegistrar("OtherRegistrar Inc.");
        transfer.setExpectedCompletion("2026-02-26");
        var transferResponse = new com.osir.mcp.models.transfer.PendingTransferListApiResponse();
        transferResponse.setTransfers(List.of(transfer));
        when(backendClient.listPendingTransfers(TEST_TOKEN)).thenReturn(transferResponse);

        PendingTransferListResult result = transferService.listPending();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTransfers().size());
        assertEquals(TEST_DOMAIN, result.getTransfers().get(0).getDomain());
        assertEquals("pending_approval", result.getTransfers().get(0).getStatus());
    }

    @Test
    void listPending_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        PendingTransferListResult result = transferService.listPending();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void listPending_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.listPendingTransfers(TEST_TOKEN)).thenThrow(new RuntimeException("Timeout"));

        PendingTransferListResult result = transferService.listPending();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
    }
}
