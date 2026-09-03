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
import io.quarkiverse.mcp.server.ToolArg;
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

    @Tool(description = "Get a transfer price quote for a domain. Requires authentication. Returns transfer price, currency, extension years, and new expiration date. Call before initiateTransfer to show the user the cost.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get transfer quote",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public TransferQuoteResult getTransferQuote(
            @ToolArg(description = "Fully qualified domain name to quote, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return transferService.getQuote(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting transfer quote: %s", e.getMessage());
            return new TransferQuoteResult(false, "Failed to get transfer quote: " + e.getMessage());
        }
    }

    @Tool(description = "Starts a transfer for a domain already prepared at the losing registrar (unlocked, auth code in hand). transferDomain (domain tools) stages transfer + registrant assignment in one step; use that when the user gives you contact details. Deducts from account balance. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Initiate domain transfer",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult initiateTransfer(
            @ToolArg(description = "Fully qualified domain name to transfer in, e.g. 'example.com'.") String domain,
            @ToolArg(description = "EPP/transfer authorization code obtained from the losing registrar.") String authCode,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "initiateTransfer",
                "Initiate transfer of '" + domain + "' to OSIR (deducts transfer fee from account balance)",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> transferService.initiateTransfer(domain, authCode)
        );
    }

    @Tool(description = "Check the current status of a domain transfer. Requires authentication. Returns status, request date, current registrar, and expected completion.",
            annotations = @Tool.Annotations(
                    title = "Get transfer status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public TransferStatusResult getTransferStatus(
            @ToolArg(description = "Fully qualified domain name whose transfer to check, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return transferService.getStatus(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting transfer status: %s", e.getMessage());
            return new TransferStatusResult(false, "Failed to get transfer status: " + e.getMessage());
        }
    }

    @Tool(description = "Stage cancellation of a pending domain transfer. DESTRUCTIVE and irreversible once executed. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Cancel domain transfer",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult cancelTransfer(
            @ToolArg(description = "Fully qualified domain name whose pending transfer to cancel, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "cancelTransfer",
                "Cancel pending domain transfer for '" + domain + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> transferService.cancelTransfer(domain)
        );
    }

    @Tool(description = "List all pending incoming (gaining) domain transfers. Requires authentication. Returns each transfer with its status, request date, current registrar, and expected completion.",
            annotations = @Tool.Annotations(
                    title = "List pending transfers",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public PendingTransferListResult listPendingTransfers(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return transferService.listPending();
        } catch (Exception e) {
            Log.errorf(e, "Error listing pending transfers: %s", e.getMessage());
            return new PendingTransferListResult(false, "Failed to list pending transfers: " + e.getMessage());
        }
    }
}
