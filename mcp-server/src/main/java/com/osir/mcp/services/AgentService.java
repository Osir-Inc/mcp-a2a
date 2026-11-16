package com.osir.mcp.services;

import com.osir.mcp.models.deploy.AgentDtos.AgentActionResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentDeployResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.AgentListResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentStatusResult;
import com.osir.mcp.models.deploy.AgentDtos.PasswordEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.ProviderEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceListResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.services.DeploymentService.AgentCall;

import static com.osir.mcp.services.DeploymentService.capitalize;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The osirAgent* tools: vetted AI agents (Hermes) on the customer's OWN VPS (app.osir.deploy
 * docs/howto_agents.md §3, CONTRACTS.md §2). An agent is a C2 app with kind "agent", created only by
 * POST /v1/agents; list/status/delete use the ordinary /v1/apps endpoints.
 *
 * <p>The VPS half (attach a box the user owns, or order one behind the confirmation gate, wait for the
 * OS build, bind DNS) is {@link MoveToOwnedService}'s, step for step — only the final call differs:
 * POST /v1/agents instead of move-to-owned.
 *
 * <p><b>The dashboard password</b> arrives in exactly two responses (install, reset). It goes into the
 * tool result for the user, once, and nowhere else: never logged, never stored, never repeated.
 */
@ApplicationScoped
public class AgentService {

    private static final Logger LOG = Logger.getLogger(AgentService.class);

    public static final String TOOL = "osirAgentDeploy";
    /** The catalog the MCP offers; C2 holds the authoritative one and answers 422 for anything else. */
    public static final Set<String> CATALOG = Set.of("hermes");
    /** C2 refuses a smaller box with BOX_TOO_SMALL; checked here first so nothing is bought for nothing. */
    public static final int MIN_MEMORY_MB = 4096;
    public static final Set<String> PROVIDERS = Set.of("deepseek", "openai", "openrouter", "anthropic");

    @Inject
    DeploymentService deploymentService;

    @Inject
    MoveToOwnedService moveToOwnedService;

    @Inject
    VpsService vpsService;

    /**
     * Agents for which ordering a NEW VPS is open (env OSIR_AGENTS_ORDERABLE, comma-separated). C2 has no
     * way to ask "is this agent released?" without starting a real install, and an unreleased agent
     * answers 503 only AFTER the box is bought. So ordering stays closed until the operator lists the
     * agent here at release time; installing on a VPS the user already owns works regardless.
     * ponytail: a second source of truth — drop it when C2 grows a read-only catalog/availability route.
     */
    @ConfigProperty(name = "osir.agents.orderable", defaultValue = "none")
    String orderable;

    /** Public half of the platform deploy key, for the BOX_KEY_REFUSED fix text. */
    @ConfigProperty(name = "osir.vps.platform-ssh-pubkey", defaultValue = "")
    String platformSshPubkey;

    HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    // ---- deploy -----------------------------------------------------------------------------------

    /** Null when {@code agent} is in the catalog, else the refusal naming what is. */
    public String unknownAgent(String agent) {
        return agent != null && CATALOG.contains(agent.toLowerCase(Locale.ROOT)) ? null
                : "Unknown agent '" + agent + "'. Available: " + String.join(", ", CATALOG) + ".";
    }

    /**
     * Every path that spends nothing: install on a VPS the user owns ({@code instanceId}), or resume an
     * install whose VPS was already ordered. Returns null when the only way forward is ordering a VPS
     * (the caller stages that behind the confirmation gate).
     */
    public Object deployWithoutSpending(String agent, String instanceId, String domain, String username) {
        agent = agent.toLowerCase(Locale.ROOT);
        Names names = names(agent);
        if (instanceId != null && !instanceId.isBlank()) {
            VpsInstanceDetailResult box = vpsService.getInstanceDetails(instanceId);
            Integer memoryMb = box != null && box.isSuccess() && box.getInstance() != null
                    && box.getInstance().getVpsPackage() != null ? box.getInstance().getVpsPackage().getMemoryMb() : null;
            if (memoryMb != null && memoryMb < MIN_MEMORY_MB) {
                return AgentDeployResult.fail("VPS '" + instanceId + "' has " + memoryMb + " MB of memory; "
                        + capitalize(agent) + " needs at least " + MIN_MEMORY_MB + " MB. Use a larger VPS the user "
                        + "owns (listMyVpsInstances) or order one (listVpsPackages, pass its packageId). Nothing was done.");
            }
            Optional<String> bound = names.boundTo(instanceId);
            if (bound.isPresent()) {
                return existing(bound.get(), instanceId, agent, domain, username);
            }
            String name = names.free();
            return moveToOwnedService.attachThen(name, instanceId, domain, TOOL, ship(name, agent, domain, username));
        }
        // An install whose VPS was ordered already: never order again.
        for (String name : names.candidates()) {
            if (moveToOwnedService.hasOrderedInstance(name)) {
                return moveToOwnedService.resumeThen(name, domain, TOOL, ship(name, agent, domain, username));
            }
        }
        Optional<VpsInstanceSummary> orphan = names.orderedBoxWithoutAgent();
        if (orphan.isPresent()) {
            String name = names.nameOfBox(orphan.get());
            LOG.infof("agentDeploy: VPS %s was ordered for %s but the agent was never installed; installing now",
                    orphan.get().getId(), name);
            return moveToOwnedService.attachThen(name, orphan.get().getId(), domain, TOOL,
                    ship(name, agent, domain, username));
        }
        // The DURABLE check, read from C2 (survives an MCP restart and a released tracker): the user already
        // has an agent of this kind. An unfinished one is resumed; a finished one is reported. Ordering is
        // reserved for a user who has none — a retry after a timed-out install must never buy a second VPS.
        // ponytail: one agent per kind via ordering; a second one is installed on a VPS they own (instanceId).
        String installed = null;
        for (String name : names.agentNames()) {
            String box = names.boxOf(name);
            OwnedMoveDto move = deploymentService.getStatus(name).ownedMove();
            boolean done = move == null ? box != null : isInstalled(move);
            if (!done && box != null) {
                return existing(name, box, agent, domain, username);
            }
            if (done && installed == null) {
                installed = name;
            }
        }
        if (installed != null) {
            return alreadyInstalled(installed, names.boxOf(installed), agent,
                    " To install another one, use a different VPS the user owns (instanceId).");
        }
        return null;
    }

    /** An agent already on {@code instanceId}: report it, wait for it, or retry its install - never a new password. */
    private Object existing(String name, String instanceId, String agent, String domain, String username) {
        AppStatusResult s = deploymentService.getStatus(name);
        OwnedMoveDto move = s.success() ? s.ownedMove() : null;
        // No move row but bound to a box: installed long enough ago that C2 no longer reports the move.
        if (move == null ? s.success() && s.ownedInstanceId() != null : isInstalled(move)) {
            return alreadyInstalled(name, instanceId, agent, "");
        }
        if (move != null && "MOVING".equalsIgnoreCase(move.state())) {
            // Re-sending the install now would re-dispatch one that is running.
            return new AgentDeployResult(true, "INSTALLING", capitalize(agent) + " is still being installed on VPS '"
                    + instanceId + "' as '" + name + "'. Nothing was re-sent.", s.app() == null ? name : s.app().appId(),
                    s.app() == null ? null : s.app().liveUrl(), null, null, instanceId, null, null, null,
                    "Check osirAgentStatus every minute until it says installed; do not call " + TOOL + " again meanwhile.");
        }
        // FAILED / REFUSED / not started: re-sending resumes it (C2 returns no new password on a retry).
        return moveToOwnedService.attachThen(name, instanceId, domain, TOOL, ship(name, agent, domain, username));
    }

    private AgentDeployResult alreadyInstalled(String name, String instanceId, String agent, String extra) {
        AppDto app = deploymentService.getStatus(name).app();
        return new AgentDeployResult(true, "INSTALLED", capitalize(agent) + " is already installed"
                + (instanceId == null ? "" : " on VPS '" + instanceId + "'") + " as '" + name + "'. Nothing was changed "
                + "and nothing was ordered." + extra, app == null ? name : app.appId(), app == null ? null : app.liveUrl(),
                null, null, instanceId, null, null, null, "Use osirAgentStatus to check it, osirAgentConfigure to connect "
                        + "a model provider, or osirAgentResetPassword if the user lost the password.");
    }

    static boolean isInstalled(OwnedMoveDto move) {
        return "MOVED".equalsIgnoreCase(move.state()) || DeploymentService.AGENT_INSTALLED_STAGE.equals(move.stage());
    }

    /** What ordering a VPS for an agent needs, resolved BEFORE staging so failures surface pre-confirm. */
    public record OrderPlan(String agent, MoveToOwnedService.Prepared prep) {
    }

    public OrderPlan planOrder(String agent, String packageId) {
        agent = agent.toLowerCase(Locale.ROOT);
        if (!orderableAgents().contains(agent)) {
            throw new IllegalStateException("Ordering a new VPS for " + capitalize(agent) + " is not open yet. "
                    + "If the user already has a VPS with at least " + MIN_MEMORY_MB + " MB of memory, call "
                    + TOOL + " with its instanceId (listMyVpsInstances) instead. Nothing was ordered.");
        }
        return new OrderPlan(agent, moveToOwnedService.prepareAgentBox(packageId, MIN_MEMORY_MB));
    }

    /**
     * Runs INSIDE the confirmed action. Re-checks everything first, so a second staged confirmation (or one
     * executed after the first finished) resumes or reports the existing install instead of ordering again.
     * The name is chosen HERE, at execute time, not when the order was staged.
     */
    public Object orderAndInstall(OrderPlan plan, String packageId, String domain, String username) {
        Object existing = deployWithoutSpending(plan.agent(), null, domain, username);
        if (existing != null) {
            return existing;
        }
        String name = names(plan.agent()).free();
        return moveToOwnedService.orderThen(name, packageId, plan.prep(), domain, TOOL,
                ship(name, plan.agent(), domain, username));
    }

    private Set<String> orderableAgents() {
        Set<String> out = new HashSet<>();
        for (String s : orderable.split(",")) {
            if (!s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** The agent's final step once the box is built: POST /v1/agents (in place of move-to-owned), then DNS. */
    private MoveToOwnedService.Ship ship(String name, String agent, String domain, String username) {
        return (moveKey, instanceId, instance) -> {
            String ip = instance.getIpAddress();
            String host = domain == null || domain.isBlank() ? null : domain.trim().toLowerCase(Locale.ROOT);
            AgentCall<AgentInstallEnvelope> call = deploymentService.installAgent(name, agent, instanceId, ip, host,
                    username == null || username.isBlank() ? null : username.trim());
            // Released either way: C2 owns the install from here. A retry finds the box again through C2's
            // binding or the box's hostname (deployWithoutSpending), so it never buys a second one.
            moveToOwnedService.release(moveKey);
            if (!call.ok()) {
                // instanceId travels with the failure: the recovery text says "retry with the same instanceId",
                // and for a VPS this tool just ordered the model has no other way to learn it.
                return new AgentDeployResult(false, "FAILED", installRefusal(agent, call), null, null, null, null,
                        instanceId, ip, host, null, "Retry with " + TOOL + "(agent='" + agent + "', instanceId='"
                                + instanceId + "') once the cause is fixed - never with packageId, which would order another VPS.");
            }
            AgentInstallEnvelope r = call.body();
            Boolean dnsBound = host == null ? null : moveToOwnedService.bindDomain(host, ip);
            String url = host != null ? "https://" + host : r.app() == null ? null : r.app().liveUrl();
            String agentId = r.app() == null ? name : r.app().appId();
            String dns = Boolean.FALSE.equals(dnsBound)
                    ? "The domain '" + host + "' could not be pointed at the VPS automatically (its DNS is not hosted "
                            + "here). Tell the user to add an A record for it pointing to " + ip + " at their DNS provider. "
                    : "";
            String wait = "Check osirAgentStatus (agentId '" + agentId + "') every minute until it says installed - the "
                    + "first install downloads a multi-GB image and takes several minutes. Then connect a model provider "
                    + "with osirAgentConfigure: " + capitalize(agent) + " does nothing without one.";
            if (r.password() == null) {
                // A retry resumed an install created earlier: C2 never returns the password twice.
                return new AgentDeployResult(true, "INSTALLING", "Installation resumed for '" + name + "' on VPS '"
                        + instanceId + "'. The dashboard password was shown when the agent was first created; if the "
                        + "user no longer has it, use osirAgentResetPassword.", agentId, url, r.username(), null,
                        instanceId, ip, host, dnsBound, dns + wait);
            }
            return new AgentDeployResult(true, "INSTALLING", capitalize(agent) + " is being installed on the user's "
                    + "VPS '" + instanceId + "' (" + ip + ") as '" + name + "'.", agentId, url, r.username(), r.password(),
                    instanceId, ip, host, dnsBound,
                    "Give the user the URL, the username and the password NOW, in this reply: the password is shown "
                            + "only this once, cannot be retrieved later, and must not be repeated afterwards. " + dns + wait);
        };
    }

    private static String installRefusal(String agent, AgentCall<?> call) {
        String why = DeploymentService.asClause(call.error() == null ? null : call.error().message(), "unknown reason");
        if (call.status() == 503) {
            return capitalize(agent) + " is not available on Osir yet: " + why + ". Tell the user plainly; nothing was "
                    + "installed. Do not try to install it any other way (no Dockerfile, no osirAppDeploy, no SSH).";
        }
        if (call.status() == 409) {
            return "The platform refused to install " + capitalize(agent) + " on this VPS: " + why + ". Tell the user what "
                    + "it says; if it names something on the VPS they must change, retry " + TOOL
                    + " with the same instanceId after they have.";
        }
        if (call.status() == 422) {
            return "The platform rejected the install request: " + why + ". Fix the argument it names and call " + TOOL
                    + " again.";
        }
        return "The install could not be started: " + why + ". Call " + TOOL + " again with the same arguments; it "
                + "resumes and never orders a second VPS.";
    }

    // ---- list / status ----------------------------------------------------------------------------

    public AgentListResult list() {
        AppListResult apps = deploymentService.listApps();
        if (!apps.success()) {
            return AgentListResult.fail(apps.message());
        }
        List<AppDto> agents = apps.apps() == null ? List.of() : apps.apps().stream().filter(AppDto::isAgent).toList();
        return new AgentListResult(true, agents.size() + " agent(s).", agents);
    }

    public AgentStatusResult status(String agentId) {
        AppStatusResult s = deploymentService.getStatus(agentId);
        if (!s.success() || s.app() == null) {
            return AgentStatusResult.fail("No agent '" + agentId + "' on this account. Use osirAgentList.");
        }
        AppDto app = s.app();
        if (!app.isAgent()) {
            return AgentStatusResult.fail("'" + app.name() + "' is an app, not an agent. Use osirAppStatus for it.");
        }
        OwnedMoveDto move = s.ownedMove();
        String state = move == null ? null : move.state();
        // No move row but bound to a box = installed long enough ago that C2 no longer reports the move
        // (the same rule DeploymentService.moveNote applies to owned apps).
        boolean installed = move == null ? s.ownedInstanceId() != null && !s.ownedInstanceId().isBlank()
                : isInstalled(move);
        String url = app.liveUrl();
        Boolean protectedDashboard = installed ? dashboardProtected(url) : null;
        String message;
        if (installed) {
            message = Boolean.FALSE.equals(protectedDashboard)
                    ? "Installed, but the dashboard did NOT ask for a login at " + url + ". Tell the user to reset the "
                            + "password with osirAgentResetPassword now, and to contact Osir support if this persists."
                    : "Installed" + (url == null ? "" : " at " + url)
                            + (Boolean.TRUE.equals(protectedDashboard) ? "; the dashboard asks for a login."
                            : "; the dashboard did not answer just now (it may be restarting after a change) - check "
                                    + "again in a minute.")
                            + " If no model provider is connected yet, use osirAgentConfigure.";
        } else if ("MOVING".equalsIgnoreCase(state)) {
            message = "Installing on the user's VPS (stage " + (move.stage() == null ? "in progress" : move.stage())
                    + "). The first install downloads a multi-GB image and takes several minutes; check again in a minute.";
        } else if ("FAILED".equalsIgnoreCase(state) || "REFUSED".equalsIgnoreCase(state)) {
            String fix = DeploymentService.userFixSteps(move, platformSshPubkey, TOOL);
            message = fix != null
                    ? "The install did not complete. " + fix
                    : "The install did not complete: " + DeploymentService.asClause(move.detail(), "no reason recorded")
                            + ". Call " + TOOL + " again with the same instanceId to resume it - no new password, never "
                            + "a second VPS.";
        } else {
            message = "Not installed yet. Call " + TOOL + " to install it.";
        }
        return new AgentStatusResult(true, message, app.appId(), app.catalog(), url, installed, state,
                move == null ? null : move.stage(), protectedDashboard, move);
    }

    /** An unauthenticated GET must answer 401: the dashboard is a shell on the user's box. Null = unknown. */
    Boolean dashboardProtected(String url) {
        if (url == null || !url.startsWith("https://")) {
            return null;
        }
        try {
            HttpResponse<Void> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .GET().build(), HttpResponse.BodyHandlers.discarding());
            // 401 = protected; a 2xx page = open. Anything else (502 while restarting, 3xx, 403) proves
            // neither, and a false "your dashboard is open" alarm is worse than "could not check".
            int code = r.statusCode();
            return code == 401 ? Boolean.TRUE : code >= 200 && code < 300 ? Boolean.FALSE : null;
        } catch (Exception e) {
            LOG.debugf("dashboard probe failed for %s: %s", url, e.getClass().getSimpleName());
            return null;
        }
    }

    // ---- configure / password / logs --------------------------------------------------------------

    public AgentActionResult configure(String agentId, String provider, String apiKey) {
        String p = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!PROVIDERS.contains(p)) {
            return AgentActionResult.fail("Unknown provider '" + provider + "'. Supported: "
                    + String.join(", ", PROVIDERS.stream().sorted().toList()) + ".");
        }
        if (apiKey == null || apiKey.isBlank()) {
            return AgentActionResult.fail("apiKey is required: the user's API key for " + p + ". Ask the user for it.");
        }
        String notAgent = requireAgent(agentId);
        if (notAgent != null) {
            return AgentActionResult.fail(notAgent);
        }
        AgentCall<ProviderEnvelope> call = deploymentService.configureAgent(agentId, p, apiKey.trim());
        if (!call.ok()) {
            String why = DeploymentService.asClause(call.error() == null ? null : call.error().message(), "unknown reason");
            return AgentActionResult.fail(call.status() == 409
                    ? "The agent is not installed yet (" + why + "). Wait until osirAgentStatus says installed, then call "
                            + "osirAgentConfigure again."
                    : "The provider could not be connected: " + why + ".");
        }
        return new AgentActionResult(true, "Connected " + p + ". The key is stored as an encrypted secret (never shown "
                + "again) and the agent is restarting on the user's VPS with it, which takes a minute or two.", null,
                "Check osirAgentStatus until it says installed again, then the user can chat with it. Tell the user: "
                        + "settings changed in the agent's own dashboard are overwritten by the next change made here; "
                        + "conversations, skills and memories are kept.");
    }

    public AgentActionResult resetPassword(String agentId) {
        String notAgent = requireAgent(agentId);
        if (notAgent != null) {
            return AgentActionResult.fail(notAgent);
        }
        AgentCall<PasswordEnvelope> call = deploymentService.resetAgentPassword(agentId);
        if (!call.ok() || call.body().password() == null) {
            return AgentActionResult.fail("The password could not be reset: " + DeploymentService.asClause(
                    call.error() == null ? null : call.error().message(), "no password returned") + ". The old one still works.");
        }
        return new AgentActionResult(true, "New dashboard password set; the agent restarts with it in a minute. The old "
                + "password no longer works.", call.body().password(),
                "Give the user this password NOW, in this reply: it is shown only this once and must not be repeated "
                        + "afterwards.");
    }

    public AgentActionResult logs(String agentId) {
        String notAgent = requireAgent(agentId);
        if (notAgent != null) {
            return AgentActionResult.fail(notAgent);
        }
        return new AgentActionResult(true, "Logs of an agent on the user's VPS are not available through Osir yet.",
                null, "Use osirAgentStatus to see whether it is installed and its dashboard answers. Do not invent log "
                        + "contents and do not ask the user for SSH access.");
    }

    /** Null when {@code agentId} is one of the caller's agents, else why not. */
    public String requireAgent(String agentId) {
        AppStatusResult s = deploymentService.getStatus(agentId);
        if (!s.success() || s.app() == null) {
            return "No agent '" + agentId + "' on this account. Use osirAgentList to see the user's agents.";
        }
        return s.app().isAgent() ? null
                : "'" + s.app().name() + "' is an app, not an agent. The osirAgent* tools manage only agents.";
    }

    /** Display name for the delete confirmation. */
    public AppDto agentApp(String agentId) {
        AppStatusResult s = deploymentService.getStatus(agentId);
        return s.success() ? s.app() : null;
    }

    // ---- naming -----------------------------------------------------------------------------------

    /**
     * Agent names are picked here, never asked of the user: {@code <agent>-1}, {@code <agent>-2}, … A name
     * is taken if an app or agent has it OR a VPS is named {@code <name>-owned.osir.app} (one ordered for it).
     */
    Names names(String agent) {
        AppListResult apps = deploymentService.listApps();
        if (!apps.success()) {
            throw new IllegalStateException("Could not read the user's apps, so nothing was done: " + apps.message());
        }
        VpsInstanceListResult boxes = vpsService.listMyInstances();
        if (boxes == null || !boxes.isSuccess()) {
            // Fail closed: a VPS we cannot see could be one already ordered for an agent.
            throw new IllegalStateException("Could not read the servers on the user's account, so nothing was done "
                    + "(installing blind could buy a second VPS). Try again in a moment.");
        }
        return new Names(agent, apps.apps() == null ? List.of() : apps.apps(),
                boxes.getInstances() == null ? List.of() : boxes.getInstances());
    }

    final class Names {
        private final String agent;
        private final List<AppDto> apps;
        private final List<VpsInstanceSummary> boxes;
        private final Pattern boxName;

        Names(String agent, List<AppDto> apps, List<VpsInstanceSummary> boxes) {
            this.agent = agent;
            this.apps = apps;
            this.boxes = boxes;
            this.boxName = Pattern.compile("(" + Pattern.quote(agent) + "-\\d+)-owned\\.osir\\.app", Pattern.CASE_INSENSITIVE);
        }

        /** The agent of this catalog already on {@code instanceId}, if any: re-installing there resumes it. */
        Optional<String> boundTo(String instanceId) {
            for (AppDto a : apps) {
                if (a.isAgent() && agent.equalsIgnoreCase(a.catalog())) {
                    AppStatusResult s = deploymentService.getStatus(a.name());
                    if (s.success() && instanceId.equals(s.ownedInstanceId())) {
                        return Optional.of(a.name());
                    }
                }
            }
            return Optional.empty();
        }

        /** The names of the user's agents of this kind. */
        List<String> agentNames() {
            return apps.stream().filter(a -> a.isAgent() && agent.equalsIgnoreCase(a.catalog())).map(AppDto::name).toList();
        }

        /** The VPS an agent is on: C2's binding, else the box ordered for it ({@code <name>-owned.osir.app}). */
        String boxOf(String name) {
            AppStatusResult s = deploymentService.getStatus(name);
            if (s.success() && s.ownedInstanceId() != null && !s.ownedInstanceId().isBlank()) {
                return s.ownedInstanceId();
            }
            String host = name + "-owned.osir.app";
            return boxes.stream().filter(b -> b.getId() != null && host.equalsIgnoreCase(b.getHostname()))
                    .map(VpsInstanceSummary::getId).findFirst().orElse(null);
        }

        /** A VPS ordered for an agent ({@code <agent>-N-owned.osir.app}) that has no agent yet. */
        Optional<VpsInstanceSummary> orderedBoxWithoutAgent() {
            return boxes.stream().filter(b -> b.getId() != null && b.getHostname() != null)
                    .filter(b -> {
                        Matcher m = boxName.matcher(b.getHostname());
                        return m.matches() && apps.stream().noneMatch(a -> m.group(1).equalsIgnoreCase(a.name()));
                    })
                    .findFirst();
        }

        String nameOfBox(VpsInstanceSummary box) {
            Matcher m = boxName.matcher(box.getHostname());
            return m.matches() ? m.group(1).toLowerCase(Locale.ROOT) : free();
        }

        /** Every name an in-flight order could be tracked under. */
        List<String> candidates() {
            int max = 0;
            for (String taken : taken()) {
                Matcher m = Pattern.compile(Pattern.quote(agent) + "-(\\d+)").matcher(taken);
                if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
            }
            return java.util.stream.IntStream.rangeClosed(1, max + 1).mapToObj(i -> agent + "-" + i).toList();
        }

        String free() {
            Set<String> taken = taken();
            for (int i = 1; ; i++) {
                String name = agent + "-" + i;
                if (!taken.contains(name)) return name;
            }
        }

        private Set<String> taken() {
            Set<String> taken = new HashSet<>();
            apps.forEach(a -> taken.add(a.name().toLowerCase(Locale.ROOT)));
            for (VpsInstanceSummary b : boxes) {
                Matcher m = b.getHostname() == null ? null : boxName.matcher(b.getHostname());
                if (m != null && m.matches()) taken.add(m.group(1).toLowerCase(Locale.ROOT));
            }
            return taken;
        }
    }

}
