package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppSourceResult;
import com.osir.mcp.models.deploy.DeployDtos.AppLogsResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbResult;
import com.osir.mcp.models.deploy.DeployDtos.SetSecretResult;
import com.osir.mcp.models.deploy.DeployDtos.UploadTicketResult;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.DeploymentService;
import com.osir.mcp.services.MoveToOwnedService;
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
 * session. No tool argument may carry a runtime/isolation choice, C2 decides that.
 */
@McpAudited
@ApplicationScoped
public class DeploymentMCPServer {

    @Inject
    DeploymentService deploymentService;

    @Inject
    MoveToOwnedService moveToOwnedService;

    @Inject
    PendingActionStore pendingActionStore;

    @RequiresAuth
    @Tool(name = "osirAppCreateUpload",
            description = "osirAppCreateUpload: Create an upload ticket for deploying app source code to Osir. Returns an uploadTicket, "
                    + "a putUrl, and instructions to zip the project and upload it. After uploading, call osirAppDeploy "
                    + "with the uploadTicket. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Create app upload ticket",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public UploadTicketResult osirAppCreateUpload(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.createUpload();
        } catch (Exception e) {
            Log.errorf(e, "osirAppCreateUpload error: %s", e.getMessage());
            return UploadTicketResult.fail("Failed to create upload ticket: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppDeploy",
            description = "osirAppDeploy: Deploy an app to Osir (free tier) and get a live HTTPS URL; the app runs isolated in a "
                    + "microVM. Deploying an existing app name redeploys it (new version) and applies any secrets set "
                    + "via osirAppSetSecret. A plain static website (HTML/CSS/JS with no framework or build step) is "
                    + "also supported: it is auto-detected and served directly; pass language 'node' for it. "
                    + "Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Deploy an app",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DeployResult osirAppDeploy(
                                      @ToolArg(description = "App name: lowercase letters, digits, and hyphens, e.g. 'habit-tracker'.") String name,
                                      @ToolArg(description = "Runtime language: 'node', 'python', 'php-laravel', or 'go'; use 'node' for a plain static site.") String language,
                                      @ToolArg(description = "Upload ticket from osirAppCreateUpload, after uploading the zipped source to its putUrl.") String uploadTicket,
                                      @ToolArg(required = false, description = "Region: 'us' or 'al' ('al' is Albania/Tirana); defaults to the platform's home region.") String region,
                                      @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.deploy(name, language, region, uploadTicket);
        } catch (Exception e) {
            Log.errorf(e, "osirAppDeploy error: %s", e.getMessage());
            return DeployResult.fail("Deploy failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppList",
            description = "osirAppList: List the authenticated user's deployed Osir apps with their live URLs and status. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List deployed apps",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AppListResult osirAppList(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.listApps();
        } catch (Exception e) {
            Log.errorf(e, "osirAppList error: %s", e.getMessage());
            return AppListResult.fail("Failed to list apps: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppStatus",
            description = "osirAppStatus: Get an Osir app's current status, live URL, and health ('is my app working?'). "
                    + "If the status is BUILD_FAILED, 'recentErrors' explains why so you can fix the source "
                    + "and redeploy. 'qa' is an independent black-box check of the LIVE app after deploy: "
                    + "qa.status PASSED means it loaded and worked; FAILED means it deployed but didn't "
                    + "actually work, and qa.findings lists the problems so you can fix and redeploy. "
                    + "'ownedMove' tracks a move onto the user's own VPS, which leaves tier and status "
                    + "unchanged while it runs: state MOVING (in progress, stage says where, ~2 minutes "
                    + "in total), MOVED (done - tier reads 'owned'), FAILED or REFUSED (call "
                    + "osirAppMoveToOwned again to retry; it never orders a second server). "
                    + "Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get app status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AppStatusResult osirAppStatus(
            @ToolArg(description = "App id from osirAppList or a deploy result.") String appId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.getStatus(appId);
        } catch (Exception e) {
            Log.errorf(e, "osirAppStatus error: %s", e.getMessage());
            return AppStatusResult.fail("Failed to get app status: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppSetSecret",
            description = "osirAppSetSecret: Set an environment secret for an Osir app (e.g. DATABASE_URL, API_KEY). The value is stored "
                    + "encrypted and injected as an env var on the next osirAppDeploy of the app; it is NEVER returned "
                    + "or logged. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Set app secret",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public SetSecretResult osirAppSetSecret(
            @ToolArg(description = "App id from osirAppList.") String appId,
            @ToolArg(description = "Environment variable name, e.g. 'API_KEY'.") String key,
            @ToolArg(description = "The secret value; never returned or logged.") String value,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.setSecret(appId, key, value);
        } catch (Exception e) {
            Log.errorf("osirAppSetSecret error for %s: %s", appId, e.getMessage());   // never log the value
            return SetSecretResult.fail("Failed to set secret: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppLogs",
            description = "osirAppLogs: Get recent logs from an Osir app's microVM ('why is my app broken?'). Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get app logs",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AppLogsResult osirAppLogs(
            @ToolArg(description = "App id from osirAppList.") String appId,
            @ToolArg(required = false, description = "Number of recent log lines to return (default 100).") Integer tail,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return deploymentService.getLogs(appId, tail);
        } catch (Exception e) {
            Log.errorf("osirAppLogs error for %s: %s", appId, e.getClass().getSimpleName());
            return AppLogsResult.fail("Failed to fetch logs. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppProvisionDatabase",
            description = "osirAppProvisionDatabase: Provision a managed Postgres database for an Osir app. The connection string is stored "
                    + "as the app's DATABASE_URL secret (encrypted, injected on the next osirAppDeploy) and is NEVER "
                    + "returned. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Provision app database",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ProvisionDbResult osirAppProvisionDatabase(
                                                      @ToolArg(description = "App id from osirAppList.") String appId,
                                                      @ToolArg(required = false, description = "Database engine; only 'postgres' (the default) is supported.") String engine,
                                                      @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
                                                      McpConnection connection) {
        try {
            return deploymentService.provisionDatabase(appId, engine == null ? "postgres" : engine);
        } catch (Exception e) {
            Log.errorf("osirAppProvisionDatabase error for %s: %s", appId, e.getClass().getSimpleName());
            return ProvisionDbResult.fail("Failed to provision the database. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppGetSource",
            description = "osirAppGetSource: Get a short-lived signed download URL for an Osir app's current source zip. Use this to "
                    + "make edits to a deployed app without the user re-attaching the project: download, patch the "
                    + "files, then osirAppCreateUpload (PUT the new zip) and osirAppDeploy under the SAME name; the "
                    + "platform rebuilds and, for owned-tier apps, auto-ships the new version to the user's box. "
                    + "Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get app source",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AppSourceResult osirAppGetSource(
            @ToolArg(description = "The deployed app's name, as shown by osirAppList.") String appName,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return deploymentService.getSource(appName);
        } catch (Exception e) {
            Log.errorf(e, "osirAppGetSource error for %s: %s", appName, e.getMessage());
            return AppSourceResult.fail("Failed to get the source URL. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppMoveToOwned",
            description = "osirAppMoveToOwned: Move a deployed Osir app from the shared free tier onto a VPS owned by the user. "
                    + "TWO WAYS IN. (1) The user already owns a VPS: pass instanceId (from listMyVpsInstances) and NO "
                    + "packageId - this ATTACHES the app to that server, SPENDS NOTHING and needs no confirmation. "
                    + "(2) No server yet: pass packageId (from listVpsPackages) and the call stages a VPS order "
                    + "(COSTS MONEY): returns an actionId; present the price/summary to the user and call "
                    + "executeConfirmedAction only if they approve. Before staging any order this tool checks whether "
                    + "the user ALREADY has a box for this app (its C2 binding, then their own VPS list) and attaches "
                    + "that instead - a retry after a failed move never buys a second server. After the move starts "
                    + "the platform ships the app onto the box server-side, which takes about two minutes; watch it "
                    + "with osirAppStatus ('ownedMove'). Calling this tool again while a move is still running just "
                    + "reports its progress, and calling it after one FAILED retries the ship. If the result status "
                    + "is BUILDING or BUILD_FAILED, follow its nextStep. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Move app to owned VPS",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public Object osirAppMoveToOwned(
                                     @ToolArg(description = "The deployed app's name, as shown by osirAppList.") String appName,
                                     @ToolArg(required = false, description = "VPS package id from listVpsPackages. Required ONLY when a server has to be ordered; omit it when passing instanceId.") String packageId,
                                     @ToolArg(required = false, description = "Id of a VPS the user ALREADY owns, from listMyVpsInstances. Given this, the app is attached to that server and nothing is ordered or charged. Never invent one.") String instanceId,
                                     @ToolArg(required = false, description = "Custom domain to serve the app on; DNS is bound automatically if the domain is hosted on osir.app nameservers, otherwise the result returns the IP and manual DNS instructions.") String domain,
                                     @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
                                     McpConnection connection) {
        try {
            // The user named a box they own: attach it. Spends nothing, so no gate — and it comes
            // FIRST, before the resume shortcut, so an explicit instanceId is never overridden.
            if (instanceId != null && !instanceId.isBlank()) {
                return moveToOwnedService.attach(appName, instanceId, domain);
            }
            // A VPS was already ordered for this app, resume (poll/ship/DNS), no new spend, no gate.
            if (moveToOwnedService.hasOrderedInstance(appName)) {
                return moveToOwnedService.resume(appName, domain);
            }
            // Durable check BEFORE any order: a box they already own is attached, never re-bought.
            String existing = moveToOwnedService.findExistingBox(appName);
            if (existing != null) {
                return moveToOwnedService.attach(appName, existing, domain);
            }
            if (packageId == null || packageId.isBlank()) {
                return MoveToOwnedResult.fail("There is no server to move '" + appName + "' onto yet. Either pass "
                        + "instanceId of a VPS the user already owns (listMyVpsInstances), or pass packageId to order "
                        + "one (listVpsPackages) - ordering costs money and will need the user's confirmation.");
            }
            MoveToOwnedService.Prepared prep = moveToOwnedService.prepare(appName, packageId);
            return pendingActionStore.stage(
                    "osirAppMoveToOwned",
                    "Order a VPS (package '" + packageId + "', " + prep.osDisplayName() + ", monthly) to move app '"
                            + appName + "' onto an owned server, deducts from account balance",
                    connection.id(),
                    DestructiveOpRateLimiter.Bucket.FINANCIAL,
                    () -> moveToOwnedService.orderAndMove(appName, packageId, prep, domain)
            );
        } catch (IllegalStateException e) {
            return MoveToOwnedResult.fail(e.getMessage());
        } catch (Exception e) {
            Log.errorf(e, "osirAppMoveToOwned error for %s: %s", appName, e.getMessage());
            return MoveToOwnedResult.fail("Could not start the move right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAppDelete",
            description = "osirAppDelete: Stage deletion of an Osir app. DESTRUCTIVE and irreversible: removes its microVM, image, "
                    + "route, and data. Returns an actionId; present the summary to the user, "
                    + "then call executeConfirmedAction with the actionId if they approve. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Delete an app",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult osirAppDelete(
            @ToolArg(description = "App id from osirAppList.") String appId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "osirAppDelete",
                "Permanently delete app '" + appId + "', removes its microVM, image, route, and all data. Irreversible.",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> deploymentService.delete(appId)
        );
    }
}
