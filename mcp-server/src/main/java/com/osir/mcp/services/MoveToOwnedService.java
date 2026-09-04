package com.osir.mcp.services;

import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedResult;
import com.osir.mcp.models.dns.DnsRecord;
import com.osir.mcp.models.dns.DnsRecordListResult;
import com.osir.mcp.models.dns.DnsRecordResult;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceListResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.models.vps.VpsOrderResult;
import com.osir.mcp.models.vps.VpsOsTemplate;
import com.osir.mcp.models.vps.VpsOsTemplateListResult;
import com.osir.mcp.models.vps.VpsSshKeyListResult;
import com.osir.mcp.models.vps.VpsSshKeyResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates osirAppMoveToOwned: order a VPS on the CUSTOMER's account (staged through the
 * existing confirmation gate), wait for the OS build, then hand C2 the ready box (C2 ships the
 * image server-side, the app's files never pass through the LLM) and bind DNS.
 *
 * <p><b>Money rule:</b> {@link #orderedInstances} records the instanceId the moment an order
 * succeeds, keyed per user+app. While an entry exists this service NEVER orders again, a build
 * still in progress resumes by polling, a FAILED build is repaired with the free
 * buildVpsInstance, and only a completed move clears the entry.
 *
 * <p>A box the customer ALREADY owns is attached instead of bought: {@link #attach} skips the order
 * (and with it the confirmation gate, which gates spending, not shipping), and
 * {@link #findExistingBox} is consulted before any order is staged. That check is what makes the
 * money rule survive a restart - {@link #orderedInstances} is only the fast in-process guard, never
 * the last thing between a customer and a second charge.
 */
@ApplicationScoped
public class MoveToOwnedService {

    private static final Logger LOG = Logger.getLogger(MoveToOwnedService.class);

    long pollIntervalMs = 5_000;
    /** Proven build time ~22s; budget covers slow days without tripping client tool timeouts. */
    long pollBudgetMs = 60_000;

    @Inject
    DeploymentService deploymentService;

    @Inject
    VpsService vpsService;

    @Inject
    DnsService dnsService;

    @Inject
    AuthService authService;

    /** Public half of the platform deploy keypair, injected into the customer's box so C2 can SSH in. */
    @ConfigProperty(name = "osir.vps.platform-ssh-pubkey")
    String platformSshPubkey;

    // "user-sub|appName" -> ordered instanceId (or ORDER_PENDING while the order call is in
    // flight, the reservation that makes a concurrent/second execute unable to order again).
    // ponytail: in-memory, so a restart forgets an in-flight move - findExistingBox() is the
    // durable check that keeps that from becoming a second charge.
    private final Map<String, String> orderedInstances = new ConcurrentHashMap<>();

    /** Consecutive identical ship refusals per move - the loop-breaker for "call again to retry". */
    private final Map<String, Refusal> shipRefusals = new ConcurrentHashMap<>();

    record Refusal(String reason, int count) {}

    /** Sentinel tracker value: an order for this key is being placed right now. */
    static final String ORDER_PENDING = "__ordering__";

    /** What the confirmation lambda needs; resolved BEFORE staging so failures surface pre-confirm. */
    public record Prepared(int osTemplateId, String osDisplayName, List<Integer> sshKeyIds) {}

    public boolean hasOrderedInstance(String appName) {
        return orderedInstances.containsKey(key(appName));
    }

    /**
     * Pre-flight: app exists/runs and belongs to the caller, OS template resolved FRESH
     * (ids drift per install, never reuse a remembered one), platform key stored (idempotent).
     * Throws IllegalStateException with an LLM-usable message on any failure.
     */
    public Prepared prepare(String appName, String packageId) {
        AppStatusResult status = deploymentService.getStatus(appName);
        if (!status.success() || status.app() == null) {
            throw new IllegalStateException("App '" + appName + "' was not found on your account. "
                    + "Use osirAppList to see your apps.");
        }
        String appState = status.app().status();
        if (appState != null && !"READY".equalsIgnoreCase(appState) && !"RUNNING".equalsIgnoreCase(appState)) {
            throw new IllegalStateException("App '" + appName + "' is in state " + appState
                    + ", it must be running before it can be moved. Check osirAppStatus and fix it first.");
        }

        VpsOsTemplateListResult templates = vpsService.listOsTemplates(packageId, null, false);
        if (!templates.isSuccess() || templates.getTemplates() == null) {
            throw new IllegalStateException("Could not list OS templates for package '" + packageId
                    + "': " + templates.getMessage());
        }
        VpsOsTemplate ubuntu = templates.getTemplates().stream()
                .filter(t -> t.getId() != null && !t.isEol())
                .filter(t -> {
                    String dn = t.getDisplayName().toLowerCase();
                    return dn.contains("ubuntu") && dn.contains("24.04");
                })
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No Ubuntu 24.04 template available for package '" + packageId
                                + "'. Pick a different package via listVpsPackages."));

        VpsSshKeyResult keyResult = vpsService.storeSshKey("osir-platform-deploy", platformSshPubkey);
        if (!keyResult.isSuccess() || keyResult.getKey() == null || keyResult.getKey().getId() == null) {
            throw new IllegalStateException("Could not store the platform deploy key on your account: "
                    + keyResult.getMessage());
        }

        // The CUSTOMER'S own keys go onto the box alongside the platform key. Without them a move
        // that fails halfway leaves them locked out of a server they are paying for (panel VNC only).
        List<Integer> keyIds = new ArrayList<>();
        keyIds.add(keyResult.getKey().getId());
        VpsSshKeyListResult mine = vpsService.listSshKeys();
        if (mine.isSuccess() && mine.getKeys() != null) {
            mine.getKeys().stream()
                    .filter(k -> k.getId() != null && !Boolean.FALSE.equals(k.getEnabled()))
                    .filter(k -> !keyIds.contains(k.getId()))
                    .forEach(k -> keyIds.add(k.getId()));
        } else {
            LOG.warnf("moveToOwned: could not list the account's SSH keys (%s), the box gets the platform key only",
                    mine.getMessage());
        }

        return new Prepared(ubuntu.getId(), ubuntu.getDisplayName(), List.copyOf(keyIds));
    }

    /**
     * The DURABLE answer to "does this customer already have a box for this app?", asked BEFORE any
     * order is staged. Sources, in order of authority:
     * <ol>
     *   <li>C2's own binding (osirAppStatus -> ownedInstanceId), set the moment a move starts and
     *       unaffected by an MCP restart.</li>
     *   <li>The provider's list of the CUSTOMER'S instances: a box named
     *       {@code <appName>-owned.osir.app} is one this tool ordered for this app.</li>
     * </ol>
     * Best-effort - a lookup that errors returns null and the caller falls through to ordering.
     *
     * <p>ponytail: the spec's second source, {@code GET /v1/admin/boxes/free}, is admin-gated and
     * this server holds no admin token (and must not - it would expose every tenant's boxes). Slot
     * it in here if C2 grows a tenant-scoped free-box route.
     */
    public String findExistingBox(String appName) {
        AppStatusResult status = deploymentService.getStatus(appName);
        if (status.success() && status.ownedInstanceId() != null && !status.ownedInstanceId().isBlank()) {
            LOG.infof("moveToOwned: app %s is already bound to owned box %s (per C2)",
                    appName, status.ownedInstanceId());
            return status.ownedInstanceId();
        }
        VpsInstanceListResult mine = vpsService.listMyInstances();
        if (mine.isSuccess() && mine.getInstances() != null) {
            String hostname = appName + "-owned.osir.app";
            for (VpsInstanceSummary i : mine.getInstances()) {
                if (i.getId() != null && hostname.equalsIgnoreCase(i.getHostname())) {
                    LOG.infof("moveToOwned: app %s already has box %s (%s) on the customer's account",
                            appName, i.getId(), hostname);
                    return i.getId();
                }
            }
        }
        return null;
    }

    /**
     * Attach the app to a VPS the customer ALREADY OWNS: no order, no spend, therefore no
     * confirmation gate (the gate is on spending).
     *
     * <p>Ownership is proved by reading the instance under the CUSTOMER'S OWN session - a box that
     * is not on their account cannot be read here - so the model cannot point this at an arbitrary
     * instanceId. C2 re-checks tenancy and its one-app-one-box rules on top of that.
     */
    public MoveToOwnedResult attach(String appName, String instanceId, String domain) {
        String moveKey = key(appName);
        String tracked = orderedInstances.get(moveKey);
        if (ORDER_PENDING.equals(tracked)) {
            return MoveToOwnedResult.fail("An order for '" + appName + "' is being placed right now. "
                    + "Wait a moment, then call osirAppMoveToOwned again to check progress, do not order again.");
        }
        if (tracked != null && !tracked.equals(instanceId)) {
            return MoveToOwnedResult.fail("A move of '" + appName + "' onto VPS '" + tracked
                    + "' is already in progress, and an app can have only one owned box at a time. Call "
                    + "osirAppMoveToOwned without instanceId to resume that one.");
        }
        VpsInstanceDetailResult details = vpsService.getInstanceDetails(instanceId);
        if (!details.isSuccess() || details.getInstance() == null) {
            return MoveToOwnedResult.fail("VPS '" + instanceId + "' could not be read on your account: "
                    + details.getMessage() + ". Use listMyVpsInstances to see the servers you own and pass "
                    + "one of their ids as instanceId. Nothing was ordered.");
        }
        orderedInstances.put(moveKey, instanceId);
        LOG.infof("moveToOwned: attaching app %s to instance %s the customer already owns (no order)",
                appName, instanceId);
        return pollAndFinish(appName, moveKey, instanceId, domain);
    }

    /**
     * Runs INSIDE the confirmed action (user approved the spend). Orders exactly once, records
     * the instanceId immediately, then continues with poll + move + DNS.
     *
     * <p>The money rule is enforced HERE, not only at stage time: a model retry can stage two
     * confirmations for the same app, and both can be executed. putIfAbsent reserves the key
     * atomically, so the second execute resumes (or reports the in-flight order) instead of
     * buying a second server.
     */
    public MoveToOwnedResult orderAndMove(String appName, String packageId, Prepared prep, String domain) {
        String moveKey = key(appName);
        String existing = orderedInstances.putIfAbsent(moveKey, ORDER_PENDING);
        if (ORDER_PENDING.equals(existing)) {
            return MoveToOwnedResult.fail("An order for '" + appName + "' is being placed right now. "
                    + "Wait a moment, then call osirAppMoveToOwned again to check progress, do not order again.");
        }
        if (existing != null) {
            LOG.infof("moveToOwned: duplicate execute for app %s, resuming instance %s instead of re-ordering",
                    appName, existing);
            return pollAndFinish(appName, moveKey, existing, domain);
        }

        VpsOrderResult order;
        try {
            order = vpsService.orderVps(packageId, appName + "-owned.osir.app", "MONTHLY",
                    prep.osTemplateId(), prep.sshKeyIds());
        } catch (RuntimeException e) {
            orderedInstances.remove(moveKey, ORDER_PENDING); // release reservation, retry allowed
            throw e;
        }
        if (!order.isSuccess() || order.getInstanceId() == null) {
            orderedInstances.remove(moveKey, ORDER_PENDING); // nothing bought, retry allowed
            throw new RuntimeException("VPS order failed: " + order.getMessage());
        }
        orderedInstances.put(moveKey, order.getInstanceId());
        LOG.infof("moveToOwned: ordered instance %s for app %s", order.getInstanceId(), appName);
        return pollAndFinish(appName, moveKey, order.getInstanceId(), domain);
    }

    /** Continue a move whose VPS is already ordered, never orders again. */
    public MoveToOwnedResult resume(String appName, String domain) {
        String moveKey = key(appName);
        String instanceId = orderedInstances.get(moveKey);
        if (instanceId == null) {
            return MoveToOwnedResult.fail("No move in progress for '" + appName
                    + "'. Call osirAppMoveToOwned with a packageId to start one.");
        }
        if (ORDER_PENDING.equals(instanceId)) {
            return MoveToOwnedResult.fail("The order for '" + appName + "' is still being placed. "
                    + "Wait a moment and call osirAppMoveToOwned again, do not order again.");
        }
        return pollAndFinish(appName, moveKey, instanceId, domain);
    }

    private MoveToOwnedResult pollAndFinish(String appName, String moveKey, String instanceId, String domain) {
        long deadline = System.currentTimeMillis() + pollBudgetMs;
        while (true) {
            VpsInstanceDetailResult details = vpsService.getInstanceDetails(instanceId);
            VpsInstanceSummary instance = details.isSuccess() ? details.getInstance() : null;
            String buildState = instance == null ? null : instance.getBuildState();

            if ("COMPLETE".equalsIgnoreCase(buildState)) {
                return finishMove(appName, moveKey, instanceId, instance, domain);
            }
            if ("FAILED".equalsIgnoreCase(buildState)) {
                // Money rule: NEVER re-order. buildVpsInstance is a free, bounded rebuild.
                return new MoveToOwnedResult(false, "BUILD_FAILED",
                        "The OS install on VPS '" + instanceId + "' failed. Do NOT order again, the server is "
                                + "already paid for. Rebuild it for free, then resume the move.",
                        instanceId, instance.getIpAddress(), domain, null,
                        "1) listVpsOsTemplates(instanceId=" + instanceId + ") to resolve the current Ubuntu 24.04 "
                                + "template id. 2) buildVpsInstance with that id (confirm via executeConfirmedAction). "
                                + "3) Call osirAppMoveToOwned again with the same arguments to resume.");
            }
            if (System.currentTimeMillis() >= deadline) {
                return new MoveToOwnedResult(false, "BUILDING",
                        "VPS '" + instanceId + "' is still building (" + (buildState == null ? "status pending" : buildState)
                                + "). The order is placed, nothing more will be charged.",
                        instanceId, instance == null ? null : instance.getIpAddress(), domain, null,
                        "Call osirAppMoveToOwned again with the same arguments in a minute, it resumes and never orders twice.");
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return MoveToOwnedResult.fail("Interrupted while waiting for the VPS build. "
                        + "Call osirAppMoveToOwned again with the same arguments to resume.");
            }
        }
    }

    private MoveToOwnedResult finishMove(String appName, String moveKey, String instanceId, VpsInstanceSummary instance, String domain) {
        String ip = instance.getIpAddress();
        // Guard: a box can report COMPLETE before its IP field populates. Shipping ip=null would
        // just bounce off C2's 422, wait instead, the tracker keeps the move resumable.
        if (ip == null || ip.isBlank()) {
            return new MoveToOwnedResult(false, "BUILDING",
                    "VPS '" + instanceId + "' is built but has no IP address assigned yet. "
                            + "The order is placed, nothing more will be charged.",
                    instanceId, null, domain, null,
                    "Call osirAppMoveToOwned again with the same arguments in a minute, it resumes and never orders twice.");
        }
        // C2 is ALREADY shipping this app: re-POSTing would re-dispatch a move that is running.
        // Only MOVING suppresses. FAILED and REFUSED must still re-dispatch — a repeat call is how a
        // transient ship failure recovers (proven live 2026-09-04: scp exit 255, then done in 42s).
        OwnedMoveDto move = moveState(appName);
        if (move != null && "MOVING".equalsIgnoreCase(move.state())) {
            LOG.infof("moveToOwned: app %s is already moving (stage %s), not re-dispatching",
                    appName, move.stage());
            Boolean dnsBound = domain == null || domain.isBlank() ? null : bindDomain(domain, ip);
            orderedInstances.remove(moveKey);
            shipRefusals.remove(moveKey);
            return new MoveToOwnedResult(true, "MOVING",
                    "The platform is already moving app '" + appName + "' onto VPS '" + instanceId + "' ("
                            + ip + ") - stage " + move.stage() + " since " + move.since()
                            + ". Nothing was re-sent and nothing more will be charged.",
                    instanceId, ip, domain, dnsBound, pollAdvice(appName));
        }

        String refusal = deploymentService.moveToOwned(appName, instanceId, ip, domain);
        if (refusal != null) {
            // Tracker entry kept; C2's endpoint is idempotent per instanceId, so resuming is safe.
            // "A move is already in progress" is a state to wait out, not a failure to escalate -
            // re-POSTing the SAME instance is a 2xx poll, so this only appears when a different box
            // was named, and support cannot help with it (spec_mcp_attach_existing_vps.md §5).
            if (refusal.toLowerCase().contains("already in progress")) {
                return new MoveToOwnedResult(false, "MOVING",
                        "A move of '" + appName + "' is already running: " + refusal
                                + ". Nothing more will be charged.",
                        instanceId, ip, domain, false,
                        "Call osirAppMoveToOwned again for '" + appName + "' WITHOUT instanceId in a minute - "
                                + "that polls the move already running instead of starting another one.");
            }
            // "call again to retry" cannot be the answer forever: the same refusal three times
            // over means retrying is not the fix, so say that instead of looping the customer.
            Refusal seen = countRefusal(moveKey, refusal);
            String retryStep = seen.count() >= 3
                    ? "STOP retrying with the same arguments - this exact refusal has come back "
                            + seen.count() + " times, so another call will not change it. Tell the user what it "
                            + "says; if it is not something they can fix, ask them to contact Osir support "
                            + "quoting app '" + appName + "', VPS '" + instanceId + "' (" + ip + ")."
                    : "Address what the refusal says if you can, then call osirAppMoveToOwned again with the "
                            + "same arguments to retry the ship step (attempt " + (seen.count() + 1) + " of 3).";
            return new MoveToOwnedResult(false, "FAILED",
                    "The server is ready but the platform could not ship the app onto it: " + refusal
                            + ". Nothing more will be charged.",
                    instanceId, ip, domain, false, retryStep);
        }

        boolean dnsBound = false;
        // C2 accepted the move but ships asynchronously, the honest instruction is "poll
        // osirAppStatus until tier reads owned", not "it's done".
        String nextStep = pollAdvice(appName);
        if (domain != null && !domain.isBlank()) {
            dnsBound = bindDomain(domain, ip);
            if (!dnsBound) {
                nextStep = "The domain '" + domain + "' could not be pointed at the box automatically (there is no "
                        + "DNS zone for it here, so it is probably on external nameservers). Point an A record for "
                        + "it to " + ip + " at your DNS provider (add an AAAA record too if you use IPv6). "
                        + nextStep;
            }
        } else {
            nextStep = "No domain was given, so no DNS record was touched - the app stays reachable at its "
                    + "*.osir.app URL. " + nextStep;
        }

        // Same key the operation started with, never re-derive identity mid-flight.
        orderedInstances.remove(moveKey);
        shipRefusals.remove(moveKey);
        LOG.infof("moveToOwned: app %s handed to C2 for instance %s (%s)", appName, instanceId, ip);
        return new MoveToOwnedResult(true, "MOVING",
                "The platform is now moving app '" + appName + "' onto your VPS '" + instanceId + "' (" + ip
                        + "). This takes a few minutes; the move is complete when osirAppStatus shows tier 'owned'. "
                        + "The shared-tier copy keeps running until you remove it. Nothing further was ordered "
                        + "or charged for this step.",
                instanceId, ip, domain, dnsBound, nextStep);
    }

    /**
     * Bind the apex A record to the new box when the zone is hosted with us. ALWAYS re-lists for
     * the current record id right before updating, ids are content-derived, a stale id is a 500.
     */
    private boolean bindDomain(String domain, String ip) {
        DnsRecordListResult zone = dnsService.listRecords(domain);
        if (!zone.isSuccess() || zone.getRecords() == null) {
            // "No zone" is indistinguishable from "external nameservers" from here, and a domain of
            // ours can simply never have had its zone created - that is the "Zone not found in
            // PowerDNS" the customer hit. Initialize once and re-list before giving up.
            if (!dnsService.initializeZone(domain).isSuccess()) {
                return false; // external NS (or zone unreadable), caller returns manual instructions
            }
            zone = dnsService.listRecords(domain);
            if (!zone.isSuccess() || zone.getRecords() == null) {
                return false;
            }
        }
        DnsRecord apexA = zone.getRecords().stream()
                .filter(r -> "A".equalsIgnoreCase(r.getType()))
                .filter(r -> r.getName() == null || r.getName().isBlank()
                        || "@".equals(r.getName()) || domain.equalsIgnoreCase(r.getName()))
                .findFirst()
                .orElse(null);
        DnsRecordResult result = apexA != null
                ? dnsService.updateRecord(domain, apexA.getId(), apexA.getName(), "A", ip, apexA.getTtl(), null)
                : dnsService.createRecord(domain, "@", "A", ip, 3600, null);
        if (!result.isSuccess()) {
            LOG.warnf("moveToOwned: DNS bind failed for %s -> %s: %s", domain, ip, result.getMessage());
        }
        return result.isSuccess();
    }

    /**
     * How the caller should watch a move that is under way. Real timings from the 2026-09-04 run:
     * box prep ~60s (~4s when a move resumes), image ship ~40s. The ten minutes seen once was a C2
     * host-key bug, since fixed — do not pace off it.
     */
    private static String pollAdvice(String appName) {
        return "Check osirAppStatus for '" + appName + "' every 20-30 seconds until tier reads 'owned' "
                + "- about two minutes in total (box prep ~60s, image ship ~40s). ownedMove.stage in that "
                + "response shows where it is; ownedMove.state FAILED means call osirAppMoveToOwned again, "
                + "which retries the ship and never orders a second server.";
    }

    /**
     * C2's durable view of this app's move (audit-derived, so it survives a C2 restart). Best-effort:
     * an unreadable status returns null, and the caller then dispatches as before — a status blip
     * must never block a move.
     */
    private OwnedMoveDto moveState(String appName) {
        AppStatusResult status = deploymentService.getStatus(appName);
        return status == null || !status.success() ? null : status.ownedMove();
    }

    /** Tally consecutive identical refusals for one move; a different reason restarts the count. */
    private Refusal countRefusal(String moveKey, String reason) {
        Refusal prev = shipRefusals.get(moveKey);
        Refusal now = prev != null && reason.equals(prev.reason())
                ? new Refusal(reason, prev.count() + 1)
                : new Refusal(reason, 1);
        shipRefusals.put(moveKey, now);
        return now;
    }

    /**
     * Money-rule tracker key: the caller's stable subject + app. FAIL-CLOSED, if the subject
     * cannot be resolved we refuse rather than bucket callers into a shared key: a shared bucket
     * would leak one user's move state to another AND let a re-keyed caller order twice.
     */
    private String key(String appName) {
        String token = authService.getCurrentToken();
        Map<String, Object> claims = token == null ? null
                : authService.parseJwtClaims(token.startsWith("Bearer ") ? token.substring(7) : token);
        Object sub = claims == null ? null : claims.get("sub");
        if (sub == null || sub.toString().isBlank()) {
            throw new IllegalStateException("Could not resolve your identity from the session token. "
                    + "Re-authenticate (loginWithDevice or reconnect) and try again, nothing was ordered.");
        }
        return sub + "|" + appName;
    }
}
