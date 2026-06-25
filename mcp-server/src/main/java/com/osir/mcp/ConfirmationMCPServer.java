package com.osir.mcp;

import com.osir.mcp.models.confirmation.ActionExecutionResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingAction;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class ConfirmationMCPServer {

    private static final Logger AUDIT = Logger.getLogger("com.osir.mcp.audit");

    @Inject
    PendingActionStore pendingActionStore;

    @Inject
    DestructiveOpRateLimiter rateLimiter;

    @Tool(description = "Execute a previously staged destructive or financial action after user approval. Required: actionId (the UUID returned by the staging tool). The action expires after 5 minutes and can only be executed once.")
    public ActionExecutionResult executeConfirmedAction(String actionId, McpConnection connection) {
        var opt = pendingActionStore.claim(actionId);
        if (opt.isEmpty()) {
            return new ActionExecutionResult(false,
                    "Action not found or already executed. It may have expired (actions expire after 5 minutes) or been claimed already.");
        }

        PendingAction pending = opt.get();

        if (pending.expiresAt() < System.currentTimeMillis()) {
            return new ActionExecutionResult(false,
                    "Action '" + pending.toolName() + "' has expired. Please retry the original operation.");
        }

        if (!pending.connectionId().equals(connection.id())) {
            return new ActionExecutionResult(false,
                    "This action was staged by a different session and cannot be executed here.");
        }

        if (!rateLimiter.tryAcquire(connection.id(), pending.bucket())) {
            return new ActionExecutionResult(false, "Rate limit exceeded. Please wait before retrying.");
        }

        AUDIT.infof("tool=%s confirmed action_id=%s conn=%s summary=%s", pending.toolName(), actionId, connection.id(), pending.summary());

        try {
            Object result = pending.action().call();
            boolean innerSuccess = extractSuccess(result);
            String msg = innerSuccess
                    ? "Action '" + pending.toolName() + "' executed successfully."
                    : "Action '" + pending.toolName() + "' completed with errors. Check result for details.";
            Log.infof("Executed confirmed action: id=%s tool=%s conn=%s success=%b", actionId, pending.toolName(), connection.id(), innerSuccess);
            return new ActionExecutionResult(innerSuccess, msg, result);
        } catch (Exception e) {
            Log.errorf(e, "Failed to execute confirmed action: id=%s tool=%s error=%s", actionId, pending.toolName(), e.getMessage());
            return new ActionExecutionResult(false, "Action '" + pending.toolName() + "' failed: " + e.getMessage());
        }
    }

    private static boolean extractSuccess(Object result) {
        if (result == null) return true;
        try {
            return (boolean) result.getClass().getMethod("isSuccess").invoke(result);
        } catch (Exception ignored) {
            return true;
        }
    }
}
