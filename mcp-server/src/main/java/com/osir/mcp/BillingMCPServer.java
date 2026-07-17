package com.osir.mcp;

import com.osir.mcp.models.billing.*;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.BillingService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class BillingMCPServer {

    @Inject
    BillingService billingService;

    @Inject
    PendingActionStore pendingActionStore;

    @Tool(description = "Get the current account balance for the authenticated user. Requires authentication.")
    public AccountBalanceResult getAccountBalance(McpConnection connection) {
        try {
            return billingService.getAccountBalance();
        } catch (Exception e) {
            Log.errorf(e, "Error getting account balance: %s", e.getMessage());
            return new AccountBalanceResult(false, "Failed to get account balance: " + e.getMessage());
        }
    }

    @Tool(description = "List invoices for the authenticated user with optional status filtering and pagination. Requires authentication. Optional: status ('DRAFT', 'PENDING', 'PAID', 'CANCELLED', 'OVERDUE'), page (default 0), size (default 20)")
    public InvoiceListResult listInvoices(@ToolArg(required = false) String status, @ToolArg(required = false) Integer page, @ToolArg(required = false) Integer size, McpConnection connection) {
        try {
            return billingService.listInvoices(status, page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error listing invoices: %s", e.getMessage());
            return new InvoiceListResult(false, "Failed to list invoices: " + e.getMessage());
        }
    }

    @Tool(description = "Get detailed information about a specific invoice including line items. Requires authentication. Required: invoiceId (string)")
    public InvoiceDetailResult getInvoiceDetails(String invoiceId, McpConnection connection) {
        try {
            return billingService.getInvoiceDetails(invoiceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting invoice details: %s", e.getMessage());
            return new InvoiceDetailResult(false, "Failed to get invoice details: " + e.getMessage());
        }
    }

    @Tool(description = "Stage payment of an outstanding invoice from account balance. Requires authentication. Required: invoiceId (string). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult payInvoice(String invoiceId, McpConnection connection) {
        return pendingActionStore.stage(
                "payInvoice",
                "Pay invoice '" + invoiceId + "' from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> billingService.payInvoice(invoiceId)
        );
    }

    @Tool(description = "Get summary statistics of invoices: total paid, pending, overdue amounts. Requires authentication.")
    public InvoiceStatisticsResult getInvoiceStatistics(McpConnection connection) {
        try {
            return billingService.getInvoiceStatistics();
        } catch (Exception e) {
            Log.errorf(e, "Error getting invoice statistics: %s", e.getMessage());
            return new InvoiceStatisticsResult(false, "Failed to get invoice statistics: " + e.getMessage());
        }
    }

    @Tool(description = "Stage creation of a Stripe payment checkout session to add funds to account balance. Requires authentication. Required: amount (double, in USD). Optional: currency (string, default 'USD'). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult createPaymentSession(double amount, @ToolArg(required = false) String currency, McpConnection connection) {
        String currencyLabel = currency != null ? currency : "USD";
        return pendingActionStore.stage(
                "createPaymentSession",
                "Create Stripe payment session for " + amount + " " + currencyLabel,
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> billingService.createPaymentSession(amount, currency)
        );
    }

    @Tool(description = "Get payment transaction history for the authenticated user. Requires authentication. Optional: page (integer), size (integer)")
    public TransactionListResult getPaymentTransactions(@ToolArg(required = false) Integer page, @ToolArg(required = false) Integer size, McpConnection connection) {
        try {
            return billingService.getPaymentTransactions(page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error getting payment transactions: %s", e.getMessage());
            return new TransactionListResult(false, "Failed to get payment transactions: " + e.getMessage());
        }
    }

    @Tool(description = "Preview the fees that would be charged for a given payment amount. Requires authentication. Required: amount (double). Optional: currency (string)")
    public FeePreviewResult previewPaymentFees(double amount, @ToolArg(required = false) String currency, McpConnection connection) {
        try {
            return billingService.previewPaymentFees(amount, currency);
        } catch (Exception e) {
            Log.errorf(e, "Error previewing fees: %s", e.getMessage());
            return new FeePreviewResult(false, "Failed to preview fees: " + e.getMessage());
        }
    }

    @Tool(description = "Get pricing for domain extensions from the product catalog. Requires authentication. Optional: extension (e.g., 'com', 'net', 'org') to filter results")
    public DomainPricingResult getDomainPricing(@ToolArg(required = false) String extension, McpConnection connection) {
        try {
            return billingService.getDomainPricing(extension);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain pricing: %s", e.getMessage());
            return new DomainPricingResult(false, "Failed to get domain pricing: " + e.getMessage());
        }
    }
}
