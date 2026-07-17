package com.osir.mcp.services;

import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.models.billing.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BillingServiceTest {

    @Mock
    BillingBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    BillingService billingService;

    private static final String TEST_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        billingService.paymentSuccessUrl = "https://lynx.osir.com/dashboard/billing/add-funds/success?session_id={CHECKOUT_SESSION_ID}";
        billingService.paymentCancelUrl = "https://lynx.osir.com/dashboard/billing/add-funds?cancelled=true";
    }

    // ===== getAccountBalance =====

    @Test
    void getAccountBalance_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        BalanceResponse response = new BalanceResponse();
        response.setBalance("125.50");
        response.setCurrency("USD");
        when(backendClient.getAccountBalance(TEST_TOKEN)).thenReturn(response);

        AccountBalanceResult result = billingService.getAccountBalance();

        assertTrue(result.isSuccess());
        assertEquals("125.50", result.getBalance());
        assertEquals("USD", result.getCurrency());
    }

    @Test
    void getAccountBalance_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        AccountBalanceResult result = billingService.getAccountBalance();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void getAccountBalance_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getAccountBalance(TEST_TOKEN)).thenThrow(new RuntimeException("Service unavailable"));

        AccountBalanceResult result = billingService.getAccountBalance();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Service unavailable"));
    }

    // ===== listInvoices =====

    @Test
    void listInvoices_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        InvoiceListResponse response = new InvoiceListResponse();
        InvoiceSummary invoice = new InvoiceSummary();
        invoice.setId("inv-1");
        invoice.setStatus("PAID");
        response.setInvoices(List.of(invoice));
        response.setTotalCount(1);
        response.setTotalPages(1);
        when(backendClient.listInvoices(null, null, null, TEST_TOKEN)).thenReturn(response);

        InvoiceListResult result = billingService.listInvoices(null, null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getInvoices().size());
        assertEquals(1, result.getTotalCount());
    }

    @Test
    void listInvoices_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        InvoiceListResult result = billingService.listInvoices(null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== getInvoiceDetails =====

    @Test
    void getInvoiceDetails_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        InvoiceDetailResponse response = new InvoiceDetailResponse();
        response.setId("inv-1");
        response.setInvoiceNumber("INV-2026-001");
        response.setStatus("PAID");
        response.setTotalAmount("50.00");
        when(backendClient.getInvoiceDetails("inv-1", TEST_TOKEN)).thenReturn(response);

        InvoiceDetailResult result = billingService.getInvoiceDetails("inv-1");

        assertTrue(result.isSuccess());
        assertEquals("INV-2026-001", result.getInvoiceNumber());
        assertEquals("50.00", result.getTotalAmount());
    }

    @Test
    void getInvoiceDetails_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        InvoiceDetailResult result = billingService.getInvoiceDetails("inv-1");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== payInvoice =====

    @Test
    void payInvoice_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        PayInvoiceResponse response = new PayInvoiceResponse();
        response.setSuccess(true);
        response.setMessage("Invoice paid");
        response.setInvoiceNumber("INV-2026-001");
        response.setAmountPaid("50.00");
        response.setRemainingBalance("75.50");
        when(backendClient.payInvoice("inv-1", TEST_TOKEN)).thenReturn(response);

        PaymentResult result = billingService.payInvoice("inv-1");

        assertTrue(result.isSuccess());
        assertEquals("50.00", result.getAmountPaid());
        assertEquals("75.50", result.getRemainingBalance());
    }

    @Test
    void payInvoice_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        PaymentResult result = billingService.payInvoice("inv-1");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void payInvoice_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.payInvoice("inv-1", TEST_TOKEN)).thenThrow(new RuntimeException("Insufficient balance"));

        PaymentResult result = billingService.payInvoice("inv-1");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Insufficient balance"));
    }

    // ===== getInvoiceStatistics =====

    @Test
    void getInvoiceStatistics_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        InvoiceStatisticsResponse response = new InvoiceStatisticsResponse();
        InvoiceStatisticsResponse.Statistics stats = new InvoiceStatisticsResponse.Statistics();
        stats.setTotalAmountPaid(50000L); // 50000 cents = $500.00
        stats.setTotalAmountPending(10000L); // 10000 cents = $100.00
        stats.setPaidCount(5);
        stats.setPendingCount(2);
        response.setStatistics(stats);
        when(backendClient.getInvoiceStatistics(TEST_TOKEN)).thenReturn(response);

        InvoiceStatisticsResult result = billingService.getInvoiceStatistics();

        assertTrue(result.isSuccess());
        assertEquals("500.00", result.getTotalPaid());
        assertEquals(5, result.getPaidCount());
    }

    @Test
    void getInvoiceStatistics_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        InvoiceStatisticsResult result = billingService.getInvoiceStatistics();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== createPaymentSession =====

    @Test
    void createPaymentSession_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        PaymentSessionResponse.Data data = new PaymentSessionResponse.Data();
        data.setSessionId("sess-123");
        data.setCheckoutUrl("https://checkout.stripe.com/pay/sess-123");
        PaymentSessionResponse response = new PaymentSessionResponse();
        response.setSuccess(true);
        response.setMessage("Session created");
        response.setData(data);
        when(backendClient.createPaymentSession(any(PaymentSessionRequest.class), eq(TEST_TOKEN))).thenReturn(response);

        PaymentSessionResult result = billingService.createPaymentSession(50.0, "USD");

        assertTrue(result.isSuccess());
        assertEquals("sess-123", result.getSessionId());
        assertNotNull(result.getCheckoutUrl());

        ArgumentCaptor<PaymentSessionRequest> captor = ArgumentCaptor.forClass(PaymentSessionRequest.class);
        verify(backendClient).createPaymentSession(captor.capture(), eq(TEST_TOKEN));
        assertEquals("stripe", captor.getValue().getProcessor());
        assertNotNull(captor.getValue().getSuccessUrl());
        assertNotNull(captor.getValue().getCancelUrl());
    }

    @Test
    void createPaymentSession_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        PaymentSessionResult result = billingService.createPaymentSession(50.0, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== getPaymentTransactions =====

    @Test
    void getPaymentTransactions_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        TransactionListResponse response = new TransactionListResponse();
        TransactionSummary tx = new TransactionSummary();
        tx.setId("tx-1");
        tx.setAmount("50.00");
        response.setTransactions(List.of(tx));
        response.setTotalCount(1);
        when(backendClient.getPaymentTransactions(null, null, TEST_TOKEN)).thenReturn(response);

        TransactionListResult result = billingService.getPaymentTransactions(null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTransactions().size());
    }

    @Test
    void getPaymentTransactions_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        TransactionListResult result = billingService.getPaymentTransactions(null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== previewPaymentFees =====

    @Test
    void previewPaymentFees_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        FeePreviewResponse response = new FeePreviewResponse();
        response.setAmount("50.00");
        response.setFee("1.50");
        response.setTotal("51.50");
        response.setCurrency("USD");
        when(backendClient.previewPaymentFees(50.0, "USD", TEST_TOKEN)).thenReturn(response);

        FeePreviewResult result = billingService.previewPaymentFees(50.0, "USD");

        assertTrue(result.isSuccess());
        assertEquals("1.50", result.getFee());
        assertEquals("51.50", result.getTotal());
    }

    @Test
    void previewPaymentFees_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        FeePreviewResult result = billingService.previewPaymentFees(50.0, "USD");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== getDomainPricing =====

    @Test
    void getDomainPricing_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        PricingEntry entry = new PricingEntry();
        entry.setTld("com");
        entry.setOperation("register");
        entry.setPrice1Year("12.99");
        when(backendClient.getDomainPricing("com", TEST_TOKEN)).thenReturn(List.of(entry));

        DomainPricingResult result = billingService.getDomainPricing("com");

        assertTrue(result.isSuccess());
        assertEquals(1, result.getPricing().size());
        assertEquals("com", result.getPricing().get(0).getTld());
    }

    @Test
    void getDomainPricing_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DomainPricingResult result = billingService.getDomainPricing("com");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }
}
