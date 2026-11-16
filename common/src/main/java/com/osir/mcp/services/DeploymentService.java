package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallBody;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.PasswordEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.ProviderBody;
import com.osir.mcp.models.deploy.AgentDtos.ProviderEnvelope;
import com.osir.mcp.models.deploy.C2Reason;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppSourceResult;
import com.osir.mcp.models.deploy.DeployDtos.SourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.C2Error;
import com.osir.mcp.models.deploy.DeployDtos.AppsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ConfirmationEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.AppLogsResult;
import com.osir.mcp.models.deploy.DeployDtos.DeleteResult;
import com.osir.mcp.models.deploy.DeployDtos.InlineFile;
import com.osir.mcp.models.deploy.DeployDtos.InlineSourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.InlineUploadBody;
import com.osir.mcp.models.deploy.DeployDtos.LogsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Thin orchestration over the Deploy backend (C2). Resolves the session token, forwards it (plus
 * the tenant) to the backend, maps responses to LLM-facing results. Tenant is derived from the
 * session JWT, NEVER from a tool argument: a caller-supplied tenant would let one customer act as
 * another.
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

    /** Public half of the platform deploy key, shown to the user when their box refuses it. Config,
     *  not code, so a key rotation needs no release. Both servers set it from OSIR_PLATFORM_SSH_PUBKEY;
     *  the blank default only keeps a misconfigured server booting (it then names Osir support). */
    @ConfigProperty(name = "osir.vps.platform-ssh-pubkey", defaultValue = "")
    String platformSshPubkey;

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

    /**
     * Deploy from files carried as tool arguments (spec_mcp_inline_deploy.md): C2 zips them into the
     * same one-time ticket the PUT path makes, then this is an ordinary deploy. Exists because a chat
     * client cannot PUT. Neither given = the old ticketless call, passed through unchanged.
     */
    public DeployResult deploy(String name, String language, String region, String uploadTicket, List<InlineFile> files) {
        boolean hasFiles = files != null && !files.isEmpty();
        if (!hasFiles) {
            return deploy(name, language, region, uploadTicket);
        }
        if (uploadTicket != null && !uploadTicket.isBlank()) {
            return DeployResult.fail("Pass either files or uploadTicket, not both. With files, leave uploadTicket out.");
        }
        String agent = refuseIfAgent(name);
        if (agent != null) {
            return DeployResult.fail(agent);
        }
        try {
            UploadEnvelope e = client.createInlineUpload(new InlineUploadBody(files), bearer(), tenant());
            return doDeploy(name, language, region, e.uploadTicket());
        } catch (jakarta.ws.rs.WebApplicationException ex) {
            // 413 (over the caps) and 422 (bad path) carry C2's message, written to name a way out
            // the caller can take. 5xx stays generic (CONTRACTS §8).
            String detail = readErrorMessage(ex.getResponse());
            LOG.errorf("inline upload failed for name=%s: %s", name, detail);
            int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();
            if (status == 413 || status == 422) {
                return DeployResult.fail("Files rejected by the platform: " + detail);
            }
            if (status == 401 || status == 403) {
                return DeployResult.fail("The platform did not accept your session (HTTP " + status + "). Log in again "
                        + "(loginWithDevice) and retry; the files themselves were not the problem.");
            }
            return status >= 400 && status < 500
                    ? DeployResult.fail("Upload refused by the platform: " + detail)
                    : DeployResult.fail("The files could not be uploaded right now. Please try again.");
        } catch (Exception ex) {
            LOG.errorf(ex, "inline upload failed for name=%s", name);
            return DeployResult.fail("The files could not be uploaded right now. Please try again.");
        }
    }

    public DeployResult deploy(String name, String language, String region, String uploadTicket) {
        String agent = refuseIfAgent(name);
        return agent != null ? DeployResult.fail(agent) : doDeploy(name, language, region, uploadTicket);
    }

    // ---- Agents are not apps ---------------------------------------------------------------------

    /** The four dashboard-login keys of a Hermes agent. Only POST /v1/agents and /password set them;
     *  osirAppSetSecret overwriting one could open the dashboard or lock the user out. */
    static final String RESERVED_SECRET_PREFIX = "HERMES_DASHBOARD_";

    /**
     * Null when {@code appRef} (an app id or name) is not an agent, else the refusal every osirApp* path
     * returns for it. Best-effort: an unreadable app is not refused here (C2 answers the real call;
     * for deploy/move it refuses an agent's name with 409 on its own).
     */
    public String refuseIfAgent(String appRef) {
        if (appRef == null || appRef.isBlank()) {
            return null;
        }
        try {
            StatusEnvelope e = client.status(appRef, bearer(), tenant());
            return e != null && e.app() != null && e.app().isAgent() ? agentRefusal(e.app()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    public static String agentRefusal(AppDto app) {
        String what = app.catalog() == null ? "an AI agent" : "a " + capitalize(app.catalog()) + " agent";
        return "'" + app.name() + "' is " + what + ", not an app - the osirApp* tools do not manage it. Use "
                + "osirAgentStatus, osirAgentConfigure, osirAgentResetPassword, osirAgentLogs or osirAgentDelete "
                + "with agentId '" + app.appId() + "' instead.";
    }

    public static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A /v1/agents call: the body on 2xx, else the HTTP status and C2's parsed error. */
    public record AgentCall<T>(T body, int status, C2Error error) {
        public boolean ok() {
            return body != null;
        }
    }

    /** POST /v1/agents. The response is never logged: it carries the dashboard password. */
    public AgentCall<AgentInstallEnvelope> installAgent(String name, String agent, String instanceId, String ip,
                                                        String domain, String username) {
        return agentCall("installAgent " + name, () -> client.installAgent(
                new AgentInstallBody(name, agent, instanceId, ip, domain, username), bearer(), tenant()));
    }

    /** POST /v1/agents/{id}/providers. The key is never logged. */
    public AgentCall<ProviderEnvelope> configureAgent(String agentId, String provider, String apiKey) {
        return agentCall("configureAgent " + agentId, () -> client.agentProvider(
                agentId, new ProviderBody(provider, apiKey), bearer(), tenant()));
    }

    /** POST /v1/agents/{id}/password. The response is never logged: it IS the password. */
    public AgentCall<PasswordEnvelope> resetAgentPassword(String agentId) {
        return agentCall("resetAgentPassword " + agentId, () -> client.agentPassword(agentId, bearer(), tenant()));
    }

    /** Runs one /v1/agents call. Logs the operation and C2's error message only, never a body. */
    private <T> AgentCall<T> agentCall(String what, java.util.function.Supplier<T> call) {
        try {
            T body = call.get();
            return body == null ? new AgentCall<>(null, 0, C2Error.of("empty response from the platform", true))
                    : new AgentCall<>(body, 200, null);
        } catch (jakarta.ws.rs.WebApplicationException ex) {
            int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();
            C2Error error = readError(ex.getResponse());
            LOG.errorf("%s refused: HTTP %d reason=%s %s", what, status, error.reason(), error.message());
            return new AgentCall<>(null, status, status >= 400 && status < 500 || status == 503 ? error
                    : C2Error.of("the platform could not complete this right now (HTTP " + status + ")", true));
        } catch (Exception ex) {
            LOG.errorf("%s failed: %s", what, ex.getClass().getSimpleName());   // no message: may echo a body
            return new AgentCall<>(null, 0, C2Error.of("the platform could not be reached", true));
        }
    }

    private DeployResult doDeploy(String name, String language, String region, String uploadTicket) {
        try {
            SourceRefBody source = (uploadTicket == null || uploadTicket.isBlank())
                    ? null : SourceRefBody.inlineArchive(uploadTicket);
            AppEnvelope e = client.deploy(new DeployAppBody(name, language, region, source), bearer(), tenant());
            var a = e.app();
            String note = "Deploy started for '" + a.name() + "'. The URL is NOT live yet: poll osirAppStatus "
                    + "until READY (typically 1-2 min, up to ~7 for a first build) before telling the user it works. "
                    + "If it ends BUILD_FAILED, read recentErrors/osirAppLogs, fix the source and redeploy.";
            if ("owned".equalsIgnoreCase(a.tier())) {
                note += " This app runs on the user's own VPS; this deploy re-ships it there and keeps its domain.";
            }
            return new DeployResult(true, note, a.appId(), a.liveUrl(), a.status());
        } catch (jakarta.ws.rs.WebApplicationException ex) {
            // Surface C2's 4xx reason — it's written for the caller (e.g. invalid name/region) and
            // the LLM needs it to fix the call. 5xx stays generic.
            String detail = readErrorMessage(ex.getResponse());
            LOG.errorf("deploy failed for name=%s: %s", name, detail);
            int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();
            if (status >= 400 && status < 500) {
                return DeployResult.fail("Deploy rejected by the platform: " + detail);
            }
            return DeployResult.fail("Deploy could not be started. Check the app name/language and try again.");
        } catch (Exception ex) {
            LOG.errorf(ex, "deploy failed for name=%s", name);
            return DeployResult.fail("Deploy could not be started. Check the app name/language and try again.");
        }
    }

    /** The human-readable message of a failed response (see {@link #readError}). */
    static String readErrorMessage(jakarta.ws.rs.core.Response response) {
        return readError(response).message();
    }

    /**
     * Parses a failed response into its parts. C2 nests them ({"error": {code, reason, message,
     * retryable, params, ref}}); other backends send a flat {"error"|"message"|"detail": "..."}.
     * Before 2026-09-19 this read "error" with asText(), which is "" for an object, so every C2
     * refusal reached the model as an empty string. Unparseable: the truncated body, else the
     * status line.
     */
    static C2Error readError(jakarta.ws.rs.core.Response response) {
        if (response == null) {
            return C2Error.of("unknown error", null);
        }
        try {
            String body = response.readEntity(String.class);
            if (body != null && !body.isBlank()) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                var error = node.get("error");
                if (error != null && error.isObject()) {
                    Map<String, String> params = new java.util.LinkedHashMap<>();
                    var p = error.get("params");
                    if (p != null && p.isObject()) {
                        p.properties().forEach(f -> params.put(f.getKey(), f.getValue().asText()));
                    }
                    return new C2Error(text(error, "code"), text(error, "reason"),
                            text(error, "message") == null ? "HTTP " + response.getStatus() : text(error, "message"),
                            error.hasNonNull("retryable") ? error.get("retryable").asBoolean() : null,
                            params.isEmpty() ? null : params, text(error, "ref"));
                }
                for (String key : new String[]{"error", "message", "detail"}) {
                    if (node.hasNonNull(key)) {
                        return C2Error.of(node.get(key).asText(), null);
                    }
                }
                return C2Error.of(body.length() > 300 ? body.substring(0, 300) : body, null); // non-JSON, truncated
            }
        } catch (Exception ignored) {
            // Fall through to the status line.
        }
        return C2Error.of("HTTP " + response.getStatus(), null);
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    /**
     * Publish a single self-contained HTML page as a static site: gate → upload ticket → zip
     * (index.html) → PUT → deploy. Exists because chat clients (Claude.ai) cannot run zip/curl, so
     * the bytes must pass through here. Same app name = redeploy (revision).
     */
    public DeployResult publishStatic(String name, String html, String region, boolean designContract) {
        html = StaticSiteGate.normalize(html);
        String problem = StaticSiteGate.check(html, designContract);
        if (problem != null) {
            return DeployResult.fail("HTML rejected — fix and call osirSitePublish again: " + problem);
        }
        UploadTicketResult ticket = createUpload();
        if (!ticket.success()) return DeployResult.fail(ticket.message());
        try {
            var zip = new java.io.ByteArrayOutputStream();
            try (var zos = new java.util.zip.ZipOutputStream(zip)) {
                zos.putNextEntry(new java.util.zip.ZipEntry("index.html"));
                zos.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            var resp = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(5)).build().send(
                    java.net.http.HttpRequest.newBuilder(java.net.URI.create(ticket.putUrl()))
                            .timeout(java.time.Duration.ofSeconds(30))
                            .header("Content-Type", "application/zip")
                            .PUT(java.net.http.HttpRequest.BodyPublishers.ofByteArray(zip.toByteArray()))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() / 100 != 2) {
                LOG.errorf("publishStatic upload failed: HTTP %d", resp.statusCode());
                return DeployResult.fail("Upload of the site failed. Please try again.");
            }
        } catch (Exception ex) {
            LOG.errorf(ex, "publishStatic upload failed for name=%s", name);
            return DeployResult.fail("Upload of the site failed. Please try again.");
        }
        return deploy(name, "node", region, ticket.uploadTicket());   // static is auto-detected by C2
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
            return new AppStatusResult(true, moveNote(e.ownedMove(), e.ownedInstanceId()), e.app(),
                    e.health(), depState, errors, e.qa(), e.ownedInstanceId(), e.boxIp(), e.ownedMove());
        } catch (Exception ex) {
            LOG.errorf(ex, "getStatus failed for %s", appId);
            return AppStatusResult.fail("Could not get the app status right now. Please try again.");
        }
    }

    /** C2's install stages for an agent (status.ownedMove.stage). */
    public static final String AGENT_INSTALLING_STAGE = "OWNED_INSTALLING_AGENT";
    public static final String AGENT_INSTALLED_STAGE = "OWNED_AGENT_INSTALLED";

    /**
     * The update loop, repeated in every owned-tier result. A model that already deployed will not
     * re-read tool descriptions, so the rule has to travel with the payload — the failure of
     * 2026-09-30 was an assistant offering to hand-write an install.sh for a box the platform
     * already ships to (spec_mcp_client_update_flow.md §4).
     */
    public static final String OWNED_UPDATE_RULE =
            "To update it, change the source and call osirAppDeploy under the SAME name - the platform "
            + "re-ships it to the box automatically. Never deploy to the box by hand, over SSH or with "
            + "an install script.";

    /**
     * A move onto an owned box leaves tier=instant and status=READY for its whole run, so a bare
     * "OK" reads as "nothing is happening" — say what is happening instead.
     */
    private String moveNote(OwnedMoveDto move, String ownedInstanceId) {
        if (move == null || move.state() == null) {
            // No move row, but the app is bound to a box: one moved long enough ago that C2 no longer
            // reports the move still answers here, and it is exactly the app an assistant is most
            // likely to try to update by hand. The binding is the durable signal, not the move row.
            return ownedInstanceId == null || ownedInstanceId.isBlank()
                    ? "OK"
                    : "OK. This app runs on the user's own VPS. " + OWNED_UPDATE_RULE;
        }
        // Every field but state is nullable on the wire; an unfilled one must not reach the model as
        // the word "null".
        String stage = move.stage() == null || move.stage().isBlank() ? "in progress" : move.stage();
        String detail = asClause(move.detail(), "no reason recorded");
        String userFix = userFixSteps(move, platformSshPubkey);
        return switch (move.state().toUpperCase()) {
            case "MOVING" -> AGENT_INSTALLING_STAGE.equals(move.stage())
                    ? "OK. The agent is being installed on the user's own VPS. The first install downloads a "
                            + "multi-GB image and takes several minutes; check again in a minute."
                    : "OK. A move onto the user's own VPS is in progress (stage " + stage
                    + "). It takes about two minutes end to end; poll this tool until tier reads 'owned'.";
            case "MOVED" -> "OK. This app runs on the user's own VPS. " + OWNED_UPDATE_RULE;
            case "FAILED", "REFUSED" -> userFix != null
                    ? "OK. The last move onto the user's own VPS did not complete. " + userFix
                    // Only an explicit false stops the retry advice: null is a row from before the flag.
                    : Boolean.FALSE.equals(move.retryable())
                    ? "OK. The last move onto the user's own VPS did not complete: " + detail + ". Retrying "
                            + "unchanged will not help: tell the user what it says, and if it is not something "
                            + "they can fix, to contact Osir support"
                            + (move.reason() == null ? "" : " quoting reason " + move.reason()) + "."
                    : "OK. The last move onto the user's own VPS did not complete: "
                            + detail + ". Calling osirAppDeployToVps again retries it - it does not order "
                            + "a second server.";
            default -> "OK";
        };
    }

    /**
     * For a move that failed on something only the USER can change on their VPS (spec §9.1): what
     * happened and what they must do, else null. Static and pure so the move tool (mcp-server) says
     * exactly the same thing. The key comes from C2's {@code params.publicKey} when sent (C2 holds
     * the private half, so it is the source of truth), else from config.
     */
    public static String userFixSteps(OwnedMoveDto move, String configuredKey) {
        return userFixSteps(move, configuredKey, "osirAppDeployToVps");
    }

    /** {@link #userFixSteps(OwnedMoveDto, String)} naming {@code retryTool} as the call that retries. */
    public static String userFixSteps(OwnedMoveDto move, String configuredKey, String retryTool) {
        if (move == null) {
            return null;
        }
        String retryLast = "Retrying without changing the VPS fails the same way, so do NOT call "
                + retryTool + " yet. ";
        String replaceWarning = "WARN them first: whatever that VPS serves today is replaced by this app. ";
        String thenRetry = "Only after they confirm, call " + retryTool + " again with the same instanceId.";
        if (move.keyRefused()) {
            String key = displayKey(move.param("publicKey"));
            if (key == null) {
                key = displayKey(configuredKey);
            }
            String keyStep = key == null
                    ? "add the Osir platform deploy key (Osir support has it)"
                    : "add this public key (safe to share) as one line: " + key;
            // Step 2 pre-empts the next refusal: C2 checks the web ports right after SSH gets in.
            return "The VPS refused the Osir deploy key. " + retryLast + "Tell the user to, on that VPS: (1) "
                    + keyStep + " to /root/.ssh/authorized_keys, and make sure root may log in with a key "
                    + "(sshd_config PermitRootLogin prohibit-password or yes); (2) stop any web server holding "
                    + "ports 80/443 (e.g. 'systemctl disable --now nginx', or apache2) - Osir serves the app "
                    + "there with its own TLS. " + replaceWarning + thenRetry;
        }
        if (C2Reason.BOX_TOO_SMALL.equals(move.reason())) {
            // C2 promises integers; anything else is not echoed into the model's context.
            String have = digits(move.param("memoryMb"));
            String need = digits(move.param("requiredMb"));
            return "The VPS is too small for this agent"
                    + (have == null ? "" : ": it has " + have + " MB of memory")
                    + (need == null ? "" : " and the agent needs at least " + need + " MB") + ". "
                    + "Retrying on this VPS fails the same way. Tell the user it needs a VPS with more memory: one "
                    + "they already own (listMyVpsInstances) or a new one (listVpsPackages), then install again "
                    + "with that one.";
        }
        if (move.portsInUse()) {
            String ports = move.param("ports");
            // C2 promises digits and commas; anything else must not be echoed into the model's context.
            ports = ports != null && ports.matches("[0-9,]{1,20}") ? ports.replace(",", "/") : "80/443";
            return "Another program holds port(s) " + ports + " on the VPS, which Osir needs to serve the app "
                    + "with its own TLS. " + retryLast + "Tell the user to stop and disable it on that VPS "
                    + "(usually a web server: e.g. 'systemctl disable --now nginx', or apache2). "
                    + replaceWarning + thenRetry;
        }
        return null;
    }

    /**
     * A backend sentence ready to embed mid-sentence: trimmed and without its closing full stop. C2's
     * reason sentences end in "." and every caller appends ". …", which read "VPS.. Retrying"
     * (spec_c2_reason_codes.md §9.3). Blank or null gives {@code fallback}.
     */
    public static String asClause(String sentence, String fallback) {
        if (sentence == null || sentence.isBlank()) {
            return fallback;
        }
        String s = sentence.strip();
        while (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1).stripTrailing();
        }
        return s.isEmpty() ? fallback : s;
    }

    private static String digits(String s) {
        return s != null && s.matches("\\d{1,7}") ? s : null;
    }

    /** "type base64" plus a neutral comment: the configured line's own comment names an internal
     *  host, and customers must not see it. Null for anything that is not a plausible key line. */
    static String displayKey(String line) {
        if (line == null) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        return parts.length >= 2 && parts[0].startsWith("ssh-") ? parts[0] + " " + parts[1] + " osir-deploy" : null;
    }

    public SetSecretResult setSecret(String appId, String key, String value) {
        // Checked here, in the one place every caller (MCP tool, A2A skill) goes through: no tool can set them.
        if (key != null && key.trim().toUpperCase(java.util.Locale.ROOT).startsWith(RESERVED_SECRET_PREFIX)) {
            return SetSecretResult.fail("'" + key + "' is reserved: it controls an AI agent's dashboard login and "
                    + "can only be changed with osirAgentResetPassword. Nothing was set.");
        }
        String agent = refuseIfAgent(appId);
        if (agent != null) {
            return SetSecretResult.fail(agent + " To connect a model provider, use osirAgentConfigure.");
        }
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
        String agent = refuseIfAgent(appId);
        if (agent != null) {
            return AppLogsResult.fail(agent);
        }
        try {
            LogsEnvelope e = client.logs(appId, tail, bearer(), tenant());
            return new AppLogsResult(true, "OK", e.logs());
        } catch (Exception ex) {
            LOG.errorf(ex, "getLogs failed for %s", appId);
            return AppLogsResult.fail("Could not fetch logs right now. Please try again.");
        }
    }

    public ProvisionDbResult provisionDatabase(String appId, String engine) {
        String agent = refuseIfAgent(appId);
        if (agent != null) {
            return ProvisionDbResult.fail(agent);
        }
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

    /**
     * Signed download URL for the app's current source zip. 404 covers both "no such app" and
     * "no retained source" (apps deployed before retention shipped) — one honest message for both,
     * since the fix is the same: (re)deploy once.
     */
    public AppSourceResult getSource(String appId) {
        return getSource(appId, false);
    }

    /** {@code inline}: return the files themselves, for a client that cannot fetch getUrl. */
    public AppSourceResult getSource(String appId, boolean inline) {
        String agent = refuseIfAgent(appId);
        if (agent != null) {
            return AppSourceResult.fail(agent);
        }
        try {
            if (inline) {
                InlineSourceEnvelope e = client.inlineSource(appId, true, bearer(), tenant());
                // C2's note names the next step (or, when truncated, why NOT to redeploy from these).
                return new AppSourceResult(true, e.note() == null ? "OK" : e.note(), e.getUrl(), null, null,
                        e.files() == null ? List.of() : e.files(), e.truncated());
            }
            SourceEnvelope e = client.source(appId, bearer(), tenant());
            return new AppSourceResult(true,
                    "Signed source download URL for '" + appId + "' (expires " + e.expiresAt() + ").",
                    e.getUrl(), e.expiresAt(),
                    "Download the zip, edit the files, then osirAppDeploy under the same name with the files "
                            + "(or osirAppCreateUpload + PUT the new zip) — the platform rebuilds and, for owned "
                            + "apps, auto-ships to the box. No shell? Call this again with inline=true instead.");
        } catch (jakarta.ws.rs.WebApplicationException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatus() == 404) {
                return AppSourceResult.fail(
                        "No retained source for '" + appId + "' — either the app doesn't exist on your account, "
                                + "or it was last deployed before source retention shipped. Redeploying it once "
                                + "(osirAppDeploy with the project's files) enables source retrieval.");
            }
            LOG.errorf(ex, "getSource failed for %s", appId);
            return AppSourceResult.fail("Could not get the source URL right now. Please try again.");
        } catch (Exception ex) {
            LOG.errorf(ex, "getSource failed for %s", appId);
            return AppSourceResult.fail("Could not get the source URL right now. Please try again.");
        }
    }

    /**
     * Hand C2 a ready VPS to move the app onto. Returns null when C2 accepted the move, else why
     * it refused: branch on {@code reason}/{@code retryable}, show {@code message}. Retry-safe: the
     * endpoint is idempotent per instanceId.
     *
     * <p>C2's 4xx messages are written FOR the caller and are the only explanation the customer
     * gets — swallowing them is why three failed retries in a row said nothing. 5xx stays generic
     * (CONTRACTS §8: never forward a raw client exception, it leaks the backend host), with
     * retryable unknown so the caller's loop-breaker still applies.
     */
    public C2Error moveToOwned(String appId, String instanceId, String ip, String domain) {
        try {
            client.moveToOwned(appId,
                    new com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedBody(instanceId, ip, domain),
                    bearer(), tenant());
            return null;
        } catch (jakarta.ws.rs.WebApplicationException ex) {
            int status = ex.getResponse() == null ? 0 : ex.getResponse().getStatus();
            C2Error error = readError(ex.getResponse());
            LOG.errorf("moveToOwned refused for app=%s instance=%s: HTTP %d reason=%s ref=%s %s",
                    appId, instanceId, status, error.reason(), error.ref(), error.message());
            return status >= 400 && status < 500 ? error
                    : C2Error.of("the platform could not ship the app onto the box (HTTP " + status + ")", null);
        } catch (Exception ex) {
            LOG.errorf(ex, "moveToOwned failed for app=%s instance=%s", appId, instanceId);
            return C2Error.of("the platform could not reach the deploy backend to ship the app", null);
        }
    }

    private String bearer() {
        // AuthService returns "<type> <token>" (e.g. "Bearer eyJ..."), ready for the header.
        return authService.getCurrentToken();
    }

    /**
     * Tenant = the session subject. FAIL-CLOSED: if there is no authenticated subject we refuse
     * rather than bucket the caller into a shared tenant (isolation safety, §1.1).
     * C2 no longer takes our word for it: its CallerIdentity derives tenant_&lt;sub&gt; from the
     * VALIDATED KeyCloak JWT and does not read X-Osir-Tenant at all (verified 2026-09-04). The
     * header stays as a compat/diagnostic hint for older backends; it grants nothing.
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
