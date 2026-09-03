package com.osir.mcp;

import com.osir.mcp.models.confirmation.ActionExecutionResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingAction;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
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

    @Inject
    McpAuthHelper authHelper;

    @Tool(description = "Execute a previously staged destructive or financial action after user approval. The action expires after 5 minutes and can only be executed once.",
            annotations = @Tool.Annotations(
                    title = "Execute confirmed action",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ActionExecutionResult executeConfirmedAction(@ToolArg(description = "The action UUID from the staging tool's response.") String actionId,@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
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

        // Ownership and rate limiting key on the authenticated user, not the MCP connection -
        // OAuth clients (Claude.ai) stage and execute on different connections for the same user.
        String principal = authHelper.currentPrincipal(connection);
        if (!pending.owner().equals(principal)) {
            return new ActionExecutionResult(false,
                    "This action was staged by a different user and cannot be executed here.");
        }

        if (!rateLimiter.tryAcquire(principal, pending.bucket())) {
            return new ActionExecutionResult(false, "Rate limit exceeded. Please wait before retrying.");
        }

        AUDIT.infof("tool=%s confirmed action_id=%s owner=%s conn=%s summary=%s", pending.toolName(), actionId, principal, connection.id(), pending.summary());

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
        // Beans expose isSuccess(), records expose success(), check both before assuming success.
        for (String accessor : new String[]{"isSuccess", "success"}) {
            try {
                return (boolean) result.getClass().getMethod(accessor).invoke(result);
            } catch (Exception ignored) {
                // try next accessor
            }
        }
        return true;
    }
}
