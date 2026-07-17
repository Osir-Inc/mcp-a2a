package com.osir.mcp.services;

import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.models.billing.*;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class BillingService {

    private static final Logger LOG = Logger.getLogger(BillingService.class);

    @Inject
    @RestClient
    BillingBackendClient backendClient;

    @Inject
    AuthService authService;

    @ConfigProperty(name = "osir.payment.success-url", defaultValue = "https://lynx.osir.com/dashboard/billing/add-funds/success?session_id={CHECKOUT_SESSION_ID}")
    String paymentSuccessUrl;

    @ConfigProperty(name = "osir.payment.cancel-url", defaultValue = "https://lynx.osir.com/dashboard/billing/add-funds?cancelled=true")
    String paymentCancelUrl;

    public AccountBalanceResult getAccountBalance() {
        if (!authService.isAuthenticated()) {
            return new AccountBalanceResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            BalanceResponse response = backendClient.getAccountBalance(token);
            AccountBalanceResult result = new AccountBalanceResult(true, "Account balance retrieved successfully");
            result.setBalance(response.getBalance());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting account balance: %s", e.getMessage());
            return new AccountBalanceResult(false, "Failed to get account balance: " + e.getMessage());
        }
    }

    public InvoiceListResult listInvoices(String status, Integer page, Integer size) {
        if (!authService.isAuthenticated()) {
            return new InvoiceListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceListResponse response = backendClient.listInvoices(status, page, size, token);
            InvoiceListResult result = new InvoiceListResult(true, "Invoices retrieved successfully");
            result.setInvoices(response.getInvoices());
            result.setTotalCount(response.getTotalCount());
            result.setTotalPages(response.getTotalPages());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing invoices: %s", e.getMessage());
            return new InvoiceListResult(false, "Failed to list invoices: " + e.getMessage());
        }
    }

    public InvoiceDetailResult getInvoiceDetails(String invoiceId) {
        if (!authService.isAuthenticated()) {
            return new InvoiceDetailResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceDetailResponse response = backendClient.getInvoiceDetails(invoiceId, token);
            InvoiceDetailResult result = new InvoiceDetailResult(true, "Invoice details retrieved successfully");
            result.setId(response.getId());
            result.setInvoiceNumber(response.getInvoiceNumber());
            result.setStatus(response.getStatus());
            result.setTotalAmount(response.getTotalAmount());
            result.setCurrency(response.getCurrency());
            result.setInvoiceDate(response.getInvoiceDate());
            result.setDueDate(response.getDueDate());
            result.setPaidDate(response.getPaidDate());
            result.setItems(response.getItems());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting invoice details for %s: %s", invoiceId, e.getMessage());
            return new InvoiceDetailResult(false, "Failed to get invoice details: " + e.getMessage());
        }
    }

    public PaymentResult payInvoice(String invoiceId) {
        if (!authService.isAuthenticated()) {
            return new PaymentResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            PayInvoiceResponse response = backendClient.payInvoice(invoiceId, token);
            PaymentResult result = new PaymentResult(response.isSuccess(), response.getMessage());
            result.setInvoiceNumber(response.getInvoiceNumber());
            result.setAmountPaid(response.getAmountPaid());
            result.setRemainingBalance(response.getRemainingBalance());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error paying invoice %s: %s", invoiceId, e.getMessage());
            return new PaymentResult(false, "Payment failed: " + e.getMessage());
        }
    }

    public InvoiceStatisticsResult getInvoiceStatistics() {
        if (!authService.isAuthenticated()) {
            return new InvoiceStatisticsResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceStatisticsResponse response = backendClient.getInvoiceStatistics(token);
            InvoiceStatisticsResult result = new InvoiceStatisticsResult(true, "Invoice statistics retrieved successfully");
            result.setTotalPaid(response.getTotalPaid());
            result.setTotalPending(response.getTotalPending());
            result.setTotalOverdue(response.getTotalOverdue());
            result.setPaidCount(response.getPaidCount());
            result.setPendingCount(response.getPendingCount());
            result.setOverdueCount(response.getOverdueCount());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting invoice statistics: %s", e.getMessage());
            return new InvoiceStatisticsResult(false, "Failed to get invoice statistics: " + e.getMessage());
        }
    }

    public PaymentSessionResult createPaymentSession(double amount, String currency) {
        if (!authService.isAuthenticated()) {
            return new PaymentSessionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            PaymentSessionRequest request = new PaymentSessionRequest(
                    "stripe", amount, currency != null ? currency : "USD", paymentSuccessUrl, paymentCancelUrl);
            PaymentSessionResponse response = backendClient.createPaymentSession(request, token);
            PaymentSessionResult result = new PaymentSessionResult(response.isSuccess(), response.getMessage());
            result.setSessionId(response.getSessionId());
            result.setCheckoutUrl(response.getCheckoutUrl());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating payment session: %s", e.getMessage());
            return new PaymentSessionResult(false, "Failed to create payment session: " + e.getMessage());
        }
    }

    public TransactionListResult getPaymentTransactions(Integer page, Integer size) {
        if (!authService.isAuthenticated()) {
            return new TransactionListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransactionListResponse response = backendClient.getPaymentTransactions(page, size, token);
            TransactionListResult result = new TransactionListResult(true, "Payment transactions retrieved successfully");
            result.setTransactions(response.getTransactions());
            result.setTotalCount(response.getTotalCount());
            result.setTotalPages(response.getTotalPages());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting payment transactions: %s", e.getMessage());
            return new TransactionListResult(false, "Failed to get payment transactions: " + e.getMessage());
        }
    }

    public FeePreviewResult previewPaymentFees(double amount, String currency) {
        if (!authService.isAuthenticated()) {
            return new FeePreviewResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            FeePreviewResponse response = backendClient.previewPaymentFees(amount, currency, token);
            FeePreviewResult result = new FeePreviewResult(true, "Fee preview retrieved successfully");
            result.setAmount(response.getAmount());
            result.setFee(response.getFee());
            result.setTotal(response.getTotal());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error previewing payment fees: %s", e.getMessage());
            return new FeePreviewResult(false, "Failed to preview fees: " + e.getMessage());
        }
    }

    @CacheResult(cacheName = "domain-pricing")
    public DomainPricingResult getDomainPricing(String extension) {
        if (!authService.isAuthenticated()) {
            return new DomainPricingResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<PricingEntry> pricing = backendClient.getDomainPricing(extension, token);
            DomainPricingResult result = new DomainPricingResult(true, "Domain pricing retrieved successfully");
            result.setPricing(pricing);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting domain pricing: %s", e.getMessage());
            return new DomainPricingResult(false, "Failed to get domain pricing: " + e.getMessage());
        }
    }
}
