package com.osir.mcp;

import com.osir.mcp.models.billing.*;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.BillingService;
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

class BillingMCPServerTest {

    @Mock
    BillingService billingService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    BillingMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    // ===== getAccountBalance =====

    @Test
    void getAccountBalance_delegatesToService() {
        AccountBalanceResult expected = new AccountBalanceResult(true, "OK");
        when(billingService.getAccountBalance()).thenReturn(expected);

        AccountBalanceResult result = mcpServer.getAccountBalance(mockConnection);

        assertSame(expected, result);
        verify(billingService).getAccountBalance();
    }

    @Test
    void getAccountBalance_handlesException() {
        when(billingService.getAccountBalance()).thenThrow(new RuntimeException("Fail"));

        AccountBalanceResult result = mcpServer.getAccountBalance(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== listInvoices =====

    @Test
    void listInvoices_delegatesToService() {
        InvoiceListResult expected = new InvoiceListResult(true, "OK");
        when(billingService.listInvoices(null, null, null)).thenReturn(expected);

        InvoiceListResult result = mcpServer.listInvoices(null, null, null, mockConnection);

        assertSame(expected, result);
        verify(billingService).listInvoices(null, null, null);
    }

    @Test
    void listInvoices_handlesException() {
        when(billingService.listInvoices(null, null, null)).thenThrow(new RuntimeException("Fail"));

        InvoiceListResult result = mcpServer.listInvoices(null, null, null, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== getInvoiceDetails =====

    @Test
    void getInvoiceDetails_delegatesToService() {
        InvoiceDetailResult expected = new InvoiceDetailResult(true, "OK");
        when(billingService.getInvoiceDetails("inv-1")).thenReturn(expected);

        InvoiceDetailResult result = mcpServer.getInvoiceDetails("inv-1", mockConnection);

        assertSame(expected, result);
        verify(billingService).getInvoiceDetails("inv-1");
    }

    @Test
    void getInvoiceDetails_handlesException() {
        when(billingService.getInvoiceDetails("inv-1")).thenThrow(new RuntimeException("Fail"));

        InvoiceDetailResult result = mcpServer.getInvoiceDetails("inv-1", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== payInvoice =====

    @Test
    void payInvoice_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "payInvoice", "summary");
        when(pendingActionStore.stage(eq("payInvoice"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.payInvoice("inv-1", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("payInvoice"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any());
    }

    // ===== getInvoiceStatistics =====

    @Test
    void getInvoiceStatistics_delegatesToService() {
        InvoiceStatisticsResult expected = new InvoiceStatisticsResult(true, "OK");
        when(billingService.getInvoiceStatistics()).thenReturn(expected);

        InvoiceStatisticsResult result = mcpServer.getInvoiceStatistics(mockConnection);

        assertSame(expected, result);
        verify(billingService).getInvoiceStatistics();
    }

    @Test
    void getInvoiceStatistics_handlesException() {
        when(billingService.getInvoiceStatistics()).thenThrow(new RuntimeException("Fail"));

        InvoiceStatisticsResult result = mcpServer.getInvoiceStatistics(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== createPaymentSession =====

    @Test
    void createPaymentSession_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "createPaymentSession", "summary");
        when(pendingActionStore.stage(eq("createPaymentSession"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.createPaymentSession(50.0, "USD", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("createPaymentSession"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any());
    }

    // ===== getPaymentTransactions =====

    @Test
    void getPaymentTransactions_delegatesToService() {
        TransactionListResult expected = new TransactionListResult(true, "OK");
        when(billingService.getPaymentTransactions(null, null)).thenReturn(expected);

        TransactionListResult result = mcpServer.getPaymentTransactions(null, null, mockConnection);

        assertSame(expected, result);
        verify(billingService).getPaymentTransactions(null, null);
    }

    @Test
    void getPaymentTransactions_handlesException() {
        when(billingService.getPaymentTransactions(null, null)).thenThrow(new RuntimeException("Fail"));

        TransactionListResult result = mcpServer.getPaymentTransactions(null, null, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== previewPaymentFees =====

    @Test
    void previewPaymentFees_delegatesToService() {
        FeePreviewResult expected = new FeePreviewResult(true, "OK");
        when(billingService.previewPaymentFees(50.0, "USD")).thenReturn(expected);

        FeePreviewResult result = mcpServer.previewPaymentFees(50.0, "USD", mockConnection);

        assertSame(expected, result);
        verify(billingService).previewPaymentFees(50.0, "USD");
    }

    @Test
    void previewPaymentFees_handlesException() {
        when(billingService.previewPaymentFees(50.0, "USD")).thenThrow(new RuntimeException("Fail"));

        FeePreviewResult result = mcpServer.previewPaymentFees(50.0, "USD", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== getDomainPricing =====

    @Test
    void getDomainPricing_delegatesToService() {
        DomainPricingResult expected = new DomainPricingResult(true, "OK");
        when(billingService.getDomainPricing("com")).thenReturn(expected);

        DomainPricingResult result = mcpServer.getDomainPricing("com", mockConnection);

        assertSame(expected, result);
        verify(billingService).getDomainPricing("com");
    }

    @Test
    void getDomainPricing_handlesException() {
        when(billingService.getDomainPricing("com")).thenThrow(new RuntimeException("Fail"));

        DomainPricingResult result = mcpServer.getDomainPricing("com", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
