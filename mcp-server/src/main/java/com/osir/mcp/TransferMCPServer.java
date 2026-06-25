package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.transfer.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.TransferService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class TransferMCPServer {

    @Inject
    TransferService transferService;

    @Inject
    PendingActionStore pendingActionStore;

    @Tool(description = "Get a transfer price quote for a domain. Requires authentication. Required: domain (e.g., 'example.com'). Returns transfer price, currency, extension years, and new expiration date.")
    public TransferQuoteResult getTransferQuote(String domain, McpConnection connection) {
        try {
            return transferService.getQuote(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting transfer quote: %s", e.getMessage());
            return new TransferQuoteResult(false, "Failed to get transfer quote: " + e.getMessage());
        }
    }

    @Tool(description = "Stage initiation of a domain transfer from another registrar. Deducts from account balance. Requires authentication. Required: domain (e.g., 'example.com'), authCode (EPP authorization code from current registrar). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult initiateTransfer(String domain, String authCode, McpConnection connection) {
        return pendingActionStore.stage(
                "initiateTransfer",
                "Initiate transfer of '" + domain + "' to OSIR (deducts transfer fee from account balance)",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> transferService.initiateTransfer(domain, authCode)
        );
    }

    @Tool(description = "Check the current status of a domain transfer. Requires authentication. Required: domain (e.g., 'example.com'). Returns status, request date, current registrar, and expected completion.")
    public TransferStatusResult getTransferStatus(String domain, McpConnection connection) {
        try {
            return transferService.getStatus(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting transfer status: %s", e.getMessage());
            return new TransferStatusResult(false, "Failed to get transfer status: " + e.getMessage());
        }
    }

    @Tool(description = "Stage cancellation of a pending domain transfer. DESTRUCTIVE — irreversible. Requires authentication. Required: domain (e.g., 'example.com'). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult cancelTransfer(String domain, McpConnection connection) {
        return pendingActionStore.stage(
                "cancelTransfer",
                "Cancel pending domain transfer for '" + domain + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> transferService.cancelTransfer(domain)
        );
    }

    @Tool(description = "List all pending incoming (gaining) domain transfers. Requires authentication. Returns a list of transfers with their status, request date, current registrar, and expected completion.")
    public PendingTransferListResult listPendingTransfers(McpConnection connection) {
        try {
            return transferService.listPending();
        } catch (Exception e) {
            Log.errorf(e, "Error listing pending transfers: %s", e.getMessage());
            return new PendingTransferListResult(false, "Failed to list pending transfers: " + e.getMessage());
        }
    }
}
