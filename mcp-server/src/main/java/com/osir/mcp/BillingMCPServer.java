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

    @Tool(description = "getAccountBalance: Get the current account balance for the authenticated user. Requires authentication.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get account balance",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AccountBalanceResult getAccountBalance(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.getAccountBalance();
        } catch (Exception e) {
            Log.errorf(e, "Error getting account balance: %s", e.getMessage());
            return new AccountBalanceResult(false, "Failed to get account balance: " + e.getMessage());
        }
    }

    @Tool(description = "listInvoices: List invoices for the authenticated user with optional status filtering and pagination. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List invoices",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public InvoiceListResult listInvoices(@ToolArg(required = false, description = "Filter by invoice status: DRAFT, PENDING, PAID, CANCELLED, or OVERDUE.") String status, @ToolArg(required = false, description = "Zero-based page number for pagination, default 0.") Integer page, @ToolArg(required = false, description = "Number of invoices per page, default 20.") Integer size, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.listInvoices(status, page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error listing invoices: %s", e.getMessage());
            return new InvoiceListResult(false, "Failed to list invoices: " + e.getMessage());
        }
    }

    @Tool(description = "getInvoiceDetails: Get detailed information about a specific invoice including line items. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get invoice details",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public InvoiceDetailResult getInvoiceDetails(@ToolArg(description = "The identifier of the invoice to fetch, as returned by listInvoices.") String invoiceId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.getInvoiceDetails(invoiceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting invoice details: %s", e.getMessage());
            return new InvoiceDetailResult(false, "Failed to get invoice details: " + e.getMessage());
        }
    }

    @Tool(description = "payInvoice: Stage payment of an outstanding invoice from account balance. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Pay invoice",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult payInvoice(@ToolArg(description = "The identifier of the outstanding invoice to pay, as returned by listInvoices.") String invoiceId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "payInvoice",
                "Pay invoice '" + invoiceId + "' from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> billingService.payInvoice(invoiceId)
        );
    }

    @Tool(description = "getInvoiceStatistics: Get summary statistics of invoices: total paid, pending, overdue amounts. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get invoice statistics",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public InvoiceStatisticsResult getInvoiceStatistics(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.getInvoiceStatistics();
        } catch (Exception e) {
            Log.errorf(e, "Error getting invoice statistics: %s", e.getMessage());
            return new InvoiceStatisticsResult(false, "Failed to get invoice statistics: " + e.getMessage());
        }
    }

    @Tool(description = "createPaymentSession: Stage a Stripe checkout session to add funds to the account balance. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve. The executed result includes checkoutUrl (hand it to the human to pay) and expiresAt; then poll getPaymentTransactions until the balance credit appears.",
            annotations = @Tool.Annotations(
                    title = "Create payment session",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult createPaymentSession(@ToolArg(description = "Amount to add to the balance, in the account currency as a decimal (e.g. 25.00).") double amount, @ToolArg(required = false, description = "3-letter ISO 4217 currency code, default USD.") String currency, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        String currencyLabel = currency != null ? currency : "USD";
        return pendingActionStore.stage(
                "createPaymentSession",
                "Create Stripe payment session for " + amount + " " + currencyLabel,
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> billingService.createPaymentSession(amount, currency)
        );
    }

    @Tool(description = "getPaymentTransactions: Get payment transaction history for the authenticated user. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get payment transactions",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public TransactionListResult getPaymentTransactions(@ToolArg(required = false, description = "Zero-based page number for pagination.") Integer page, @ToolArg(required = false, description = "Number of transactions per page.") Integer size, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.getPaymentTransactions(page, size);
        } catch (Exception e) {
            Log.errorf(e, "Error getting payment transactions: %s", e.getMessage());
            return new TransactionListResult(false, "Failed to get payment transactions: " + e.getMessage());
        }
    }

    @Tool(description = "previewPaymentFees: Preview the fees that would be charged for a given payment amount. Requires authentication.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Preview payment fees",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public FeePreviewResult previewPaymentFees(@ToolArg(description = "Payment amount to preview, in the account currency as a decimal (e.g. 25.00).") double amount, @ToolArg(required = false, description = "3-letter ISO 4217 currency code, default USD.") String currency, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.previewPaymentFees(amount, currency);
        } catch (Exception e) {
            Log.errorf(e, "Error previewing fees: %s", e.getMessage());
            return new FeePreviewResult(false, "Failed to preview fees: " + e.getMessage());
        }
    }

    @Tool(description = "getDomainPricing: Get pricing for domain extensions from the product catalog. Requires authentication.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get domain pricing",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainPricingResult getDomainPricing(@ToolArg(required = false, description = "Domain extension to filter by, without the leading dot (e.g. 'com', 'net', 'org').") String extension, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return billingService.getDomainPricing(extension);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain pricing: %s", e.getMessage());
            return new DomainPricingResult(false, "Failed to get domain pricing: " + e.getMessage());
        }
    }
}
