package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppLogsResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbResult;
import com.osir.mcp.models.deploy.DeployDtos.SetSecretResult;
import com.osir.mcp.models.deploy.DeployDtos.UploadTicketResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.DeploymentService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * LLM-facing tools for the Osir app-deploy platform (chat-native deploy to *.osir.app). All tools
 * are prefixed {@code osirApp*} so they're clearly identifiable as this product, distinct from the
 * domain/VPS/DNS tools. Thin façade over the deploy backend (C2); reuses the existing KeyCloak
 * session. No tool argument may carry a runtime/isolation choice — C2 decides that (CLAUDE.md §1.1).
 */
@McpAudited
@ApplicationScoped
public class DeploymentMCPServer {

    @Inject
    DeploymentService deploymentService;

    @Inject
    PendingActionStore pendingActionStore;

    @RequiresAuth
    @Tool(name = "osirAppCreateUpload",
            description = "Create an upload ticket for deploying app source code to Osir. Returns an uploadTicket, "
                    + "a putUrl, and instructions to zip the project and upload it. After uploading, call osirAppDeploy "
                    + "with the uploadTicket. Requires authentication.")
    public UploadTicketResult osirAppCreateUpload(McpConnection connection) {
        try {
            return deploymentService.createUpload();
        } catch (Exception e) {
            Log.errorf(e, "osirAppCreateUpload error: %s", e.getMessage());
            return UploadTicketResult.fail("Failed to create upload ticket: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppDeploy",
            description = "Deploy an app to Osir (free tier) and get a live HTTPS URL; the app runs isolated in a "
                    + "microVM. Deploying an existing app name redeploys it (new version) and applies any secrets set "
                    + "via osirAppSetSecret. Required: name (lowercase letters/digits/hyphens, e.g. 'habit-tracker'), "
                    + "language ('node'|'python'|'php-laravel'), uploadTicket (from osirAppCreateUpload, after you "
                    + "upload the zipped source). A plain static website (HTML/CSS/JS with no framework or build step) "
                    + "is also supported — it's auto-detected and served directly; pass language 'node' for it. "
                    + "Optional: region ('de'|'us'). Requires authentication.")
    public DeployResult osirAppDeploy(String name, String language, String uploadTicket,
                                      @ToolArg(required = false) String region, McpConnection connection) {
        try {
            return deploymentService.deploy(name, language, region, uploadTicket);
        } catch (Exception e) {
            Log.errorf(e, "osirAppDeploy error: %s", e.getMessage());
            return DeployResult.fail("Deploy failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppList",
            description = "List the authenticated user's deployed Osir apps with their live URLs and status. Requires authentication.")
    public AppListResult osirAppList(McpConnection connection) {
        try {
            return deploymentService.listApps();
        } catch (Exception e) {
            Log.errorf(e, "osirAppList error: %s", e.getMessage());
            return AppListResult.fail("Failed to list apps: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppStatus",
            description = "Get an Osir app's current status, live URL, and health ('is my app working?'). "
                    + "If the status is BUILD_FAILED, 'recentErrors' explains why (e.g. a missing start "
                    + "command) so you can fix the source and redeploy. Required: appId (string). "
                    + "Requires authentication.")
    public AppStatusResult osirAppStatus(String appId, McpConnection connection) {
        try {
            return deploymentService.getStatus(appId);
        } catch (Exception e) {
            Log.errorf(e, "osirAppStatus error: %s", e.getMessage());
            return AppStatusResult.fail("Failed to get app status: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppSetSecret",
            description = "Set an environment secret for an Osir app (e.g. DATABASE_URL, API_KEY). The value is stored "
                    + "encrypted and injected as an env var on the next osirAppDeploy of the app; it is NEVER returned "
                    + "or logged. Required: appId (string), key (env var name), value (string). Requires authentication.")
    public SetSecretResult osirAppSetSecret(String appId, String key, String value, McpConnection connection) {
        try {
            return deploymentService.setSecret(appId, key, value);
        } catch (Exception e) {
            Log.errorf("osirAppSetSecret error for %s: %s", appId, e.getMessage());   // never log the value
            return SetSecretResult.fail("Failed to set secret: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppLogs",
            description = "Get recent logs from an Osir app's microVM ('why is my app broken?'). "
                    + "Required: appId (string). Optional: tail (number of recent lines, default 100). "
                    + "Requires authentication.")
    public AppLogsResult osirAppLogs(String appId, @ToolArg(required = false) Integer tail, McpConnection connection) {
        try {
            return deploymentService.getLogs(appId, tail);
        } catch (Exception e) {
            Log.errorf("osirAppLogs error for %s: %s", appId, e.getClass().getSimpleName());
            return AppLogsResult.fail("Failed to fetch logs. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppProvisionDatabase",
            description = "Provision a managed Postgres database for an Osir app. The connection string is stored "
                    + "as the app's DATABASE_URL secret (encrypted, injected on the next osirAppDeploy) and is NEVER "
                    + "returned. Required: appId (string). Optional: engine ('postgres', default). Requires authentication.")
    public ProvisionDbResult osirAppProvisionDatabase(String appId, @ToolArg(required = false) String engine,
                                                      McpConnection connection) {
        try {
            return deploymentService.provisionDatabase(appId, engine == null ? "postgres" : engine);
        } catch (Exception e) {
            Log.errorf("osirAppProvisionDatabase error for %s: %s", appId, e.getClass().getSimpleName());
            return ProvisionDbResult.fail("Failed to provision the database. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppDelete",
            description = "Stage deletion of an Osir app. DESTRUCTIVE and irreversible — removes its microVM, image, "
                    + "route, and data. Required: appId (string). Returns an actionId; present the summary to the user, "
                    + "then call executeConfirmedAction with the actionId if they approve. Requires authentication.")
    public ConfirmationRequiredResult osirAppDelete(String appId, McpConnection connection) {
        return pendingActionStore.stage(
                "osirAppDelete",
                "Permanently delete app '" + appId + "' — removes its microVM, image, route, and all data. Irreversible.",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> deploymentService.delete(appId)
        );
    }
}
