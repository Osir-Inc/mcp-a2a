package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.AppsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ConfirmationEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.AppLogsResult;
import com.osir.mcp.models.deploy.DeployDtos.DeleteResult;
import com.osir.mcp.models.deploy.DeployDtos.LogsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.RecentErrorDto;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbResult;
import com.osir.mcp.models.deploy.DeployDtos.SecretBody;
import com.osir.mcp.models.deploy.DeployDtos.SetSecretResult;
import com.osir.mcp.models.deploy.DeployDtos.SourceRefBody;
import com.osir.mcp.models.deploy.DeployDtos.StatusEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.UploadEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.UploadTicketResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Thin orchestration over the Deploy backend (C2). Resolves the session token, forwards it (plus
 * the tenant) to the backend, maps responses to LLM-facing results. Tenant is derived from the
 * session JWT, NEVER from a tool argument (CLAUDE.md §3, §1.1).
 *
 * <p>Error policy: failures return a SHORT, SAFE message to the LLM and log the real cause
 * server-side — never forward raw client exceptions (they leak the backend hostname, CONTRACTS §8).
 */
@ApplicationScoped
public class DeploymentService {

    private static final Logger LOG = Logger.getLogger(DeploymentService.class);

    @Inject
    @RestClient
    DeployBackendClient client;

    @Inject
    AuthService authService;

    public UploadTicketResult createUpload() {
        try {
            UploadEnvelope e = client.createUpload(bearer(), tenant());
            String instructions = "Zip your project's root directory and upload it to the putUrl, then call "
                    + "osirAppDeploy with this uploadTicket. e.g.: "
                    + "`cd <project> && zip -r /tmp/app.zip . -x '*/node_modules/*' && "
                    + "curl -X PUT --data-binary @/tmp/app.zip '<putUrl>'`";
            return new UploadTicketResult(true, "Upload ticket created.", e.uploadTicket(), e.putUrl(), instructions);
        } catch (Exception ex) {
            LOG.errorf(ex, "createUpload failed");
            return UploadTicketResult.fail("Could not create an upload ticket right now. Please try again.");
        }
    }

    public DeployResult deploy(String name, String language, String region, String uploadTicket) {
        try {
            SourceRefBody source = (uploadTicket == null || uploadTicket.isBlank())
                    ? null : SourceRefBody.inlineArchive(uploadTicket);
            AppEnvelope e = client.deploy(new DeployAppBody(name, language, region, source), bearer(), tenant());
            var a = e.app();
            return new DeployResult(true, "Deploy started for '" + a.name() + "'. Poll osirAppStatus until READY.",
                    a.appId(), a.liveUrl(), a.status());
        } catch (Exception ex) {
            LOG.errorf(ex, "deploy failed for name=%s", name);
            return DeployResult.fail("Deploy could not be started. Check the app name/language and try again.");
        }
    }

    public AppListResult listApps() {
        try {
            AppsEnvelope e = client.listApps(bearer(), tenant());
            int n = e.apps() == null ? 0 : e.apps().size();
            return new AppListResult(true, n + " app(s).", e.apps());
        } catch (Exception ex) {
            LOG.errorf(ex, "listApps failed");
            return AppListResult.fail("Could not list apps right now. Please try again.");
        }
    }

    public AppStatusResult getStatus(String appId) {
        try {
            StatusEnvelope e = client.status(appId, bearer(), tenant());
            String depState = e.deployment() == null ? null : e.deployment().state();
            var errors = e.recentErrors() == null ? java.util.List.<RecentErrorDto>of() : e.recentErrors();
            return new AppStatusResult(true, "OK", e.app(), e.health(), depState, errors);
        } catch (Exception ex) {
            LOG.errorf(ex, "getStatus failed for %s", appId);
            return AppStatusResult.fail("Could not get the app status right now. Please try again.");
        }
    }

    public SetSecretResult setSecret(String appId, String key, String value) {
        try {
            client.setSecret(appId, new SecretBody(key, value), bearer(), tenant());
            // NOTE: never log the value (§1.8).
            return new SetSecretResult(true, "Secret '" + key + "' set. It applies on the next deploy of this app.");
        } catch (Exception ex) {
            LOG.errorf("setSecret failed for app=%s key=%s: %s", appId, key, ex.getClass().getSimpleName());
            return SetSecretResult.fail("Could not set the secret. Check the app id and key, then try again.");
        }
    }

    public AppLogsResult getLogs(String appId, Integer tail) {
        try {
            LogsEnvelope e = client.logs(appId, tail, bearer(), tenant());
            return new AppLogsResult(true, "OK", e.logs());
        } catch (Exception ex) {
            LOG.errorf(ex, "getLogs failed for %s", appId);
            return AppLogsResult.fail("Could not fetch logs right now. Please try again.");
        }
    }

    public ProvisionDbResult provisionDatabase(String appId, String engine) {
        try {
            ProvisionDbEnvelope e = client.provisionDatabase(appId, engine, bearer(), tenant());
            return new ProvisionDbResult(true, e.message(), e.secretKey());
        } catch (Exception ex) {
            LOG.errorf(ex, "provisionDatabase failed for %s", appId);
            return ProvisionDbResult.fail("Could not provision a database right now. Please try again.");
        }
    }

    /**
     * Performs the actual teardown. Called from the staged Callable AFTER the user confirms via
     * executeConfirmedAction. The MCP confirmation gates it; the backend's own confirmation is
     * satisfied within this trusted call (request → execute).
     */
    public Object delete(String appId) {
        try {
            ConfirmationEnvelope c = client.requestDelete(appId, bearer(), tenant());
            client.execute(c.confirmationId(), bearer(), tenant());
            return new DeleteResult(true, "App '" + appId + "' and all its resources were deleted.");
        } catch (Exception ex) {
            LOG.errorf(ex, "delete failed for %s", appId);
            throw new RuntimeException("Could not delete the app. Please try again.");
        }
    }

    private String bearer() {
        // AuthService returns "<type> <token>" (e.g. "Bearer eyJ..."), ready for the header.
        return authService.getCurrentToken();
    }

    /**
     * Tenant = the session subject. FAIL-CLOSED: if there is no authenticated subject we refuse
     * rather than bucket the caller into a shared tenant (isolation safety, §1.1).
     * TODO: the backend must VALIDATE this forwarded JWT and derive the tenant itself — until then
     * /v1/apps trusts the X-Osir-Tenant header (tracked as a critical backend TODO).
     */
    private String tenant() {
        String token = authService.getCurrentToken();
        Map<String, Object> claims = token == null ? null
                : authService.parseJwtClaims(token.startsWith("Bearer ") ? token.substring(7) : token);
        Object sub = claims == null ? null : claims.get("sub");
        if (sub == null || sub.toString().isBlank()) {
            throw new IllegalStateException("no authenticated subject in session token");
        }
        return "tenant_" + sub;
    }
}
