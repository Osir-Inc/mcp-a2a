package com.osir.mcp.services;

import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedResult;
import com.osir.mcp.models.dns.DnsRecord;
import com.osir.mcp.models.dns.DnsRecordListResult;
import com.osir.mcp.models.dns.DnsRecordResult;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.models.vps.VpsOrderResult;
import com.osir.mcp.models.vps.VpsOsTemplate;
import com.osir.mcp.models.vps.VpsOsTemplateListResult;
import com.osir.mcp.models.vps.VpsSshKeyResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates osirAppMoveToOwned: order a VPS on the CUSTOMER's account (staged through the
 * existing confirmation gate), wait for the OS build, then hand C2 the ready box (C2 ships the
 * image server-side — the app's files never pass through the LLM) and bind DNS.
 *
 * <p><b>Money rule:</b> {@link #orderedInstances} records the instanceId the moment an order
 * succeeds, keyed per user+app. While an entry exists this service NEVER orders again — a build
 * still in progress resumes by polling, a FAILED build is repaired with the free
 * buildVpsInstance, and only a completed move clears the entry.
 *
 * <p>ponytail: tracker is in-memory — a server restart forgets an in-flight move and the user
 * would be told to order again. Persist to the DB task store if that ever bites.
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

    /** Public half of the platform deploy keypair — injected into the customer's box so C2 can SSH in. */
    @ConfigProperty(name = "osir.vps.platform-ssh-pubkey")
    String platformSshPubkey;

    // "user-sub|appName" -> ordered instanceId (or ORDER_PENDING while the order call is in
    // flight — the reservation that makes a concurrent/second execute unable to order again).
    private final Map<String, String> orderedInstances = new ConcurrentHashMap<>();

    /** Sentinel tracker value: an order for this key is being placed right now. */
    static final String ORDER_PENDING = "__ordering__";

    /** What the confirmation lambda needs; resolved BEFORE staging so failures surface pre-confirm. */
    public record Prepared(int osTemplateId, String osDisplayName, int sshKeyId) {}

    public boolean hasOrderedInstance(String appName) {
        return orderedInstances.containsKey(key(appName));
    }

    /**
     * Pre-flight: app exists/runs and belongs to the caller, OS template resolved FRESH
     * (ids drift per install — never reuse a remembered one), platform key stored (idempotent).
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
                    + " — it must be running before it can be moved. Check osirAppStatus and fix it first.");
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

        return new Prepared(ubuntu.getId(), ubuntu.getDisplayName(), keyResult.getKey().getId());
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
                    + "Wait a moment, then call osirAppMoveToOwned again to check progress — do not order again.");
        }
        if (existing != null) {
            LOG.infof("moveToOwned: duplicate execute for app %s — resuming instance %s instead of re-ordering",
                    appName, existing);
            return pollAndFinish(appName, moveKey, existing, domain);
        }

        VpsOrderResult order;
        try {
            order = vpsService.orderVps(packageId, appName + "-owned.osir.app", "monthly",
                    prep.osTemplateId(), List.of(prep.sshKeyId()));
        } catch (RuntimeException e) {
            orderedInstances.remove(moveKey, ORDER_PENDING); // release reservation — retry allowed
            throw e;
        }
        if (!order.isSuccess() || order.getInstanceId() == null) {
            orderedInstances.remove(moveKey, ORDER_PENDING); // nothing bought — retry allowed
            throw new RuntimeException("VPS order failed: " + order.getMessage());
        }
        orderedInstances.put(moveKey, order.getInstanceId());
        LOG.infof("moveToOwned: ordered instance %s for app %s", order.getInstanceId(), appName);
        return pollAndFinish(appName, moveKey, order.getInstanceId(), domain);
    }

    /** Continue a move whose VPS is already ordered — never orders again. */
    public MoveToOwnedResult resume(String appName, String domain) {
        String moveKey = key(appName);
        String instanceId = orderedInstances.get(moveKey);
        if (instanceId == null) {
            return MoveToOwnedResult.fail("No move in progress for '" + appName
                    + "'. Call osirAppMoveToOwned with a packageId to start one.");
        }
        if (ORDER_PENDING.equals(instanceId)) {
            return MoveToOwnedResult.fail("The order for '" + appName + "' is still being placed. "
                    + "Wait a moment and call osirAppMoveToOwned again — do not order again.");
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
                        "The OS install on VPS '" + instanceId + "' failed. Do NOT order again — the server is "
                                + "already paid for. Rebuild it for free, then resume the move.",
                        instanceId, instance.getIpAddress(), domain, null,
                        "1) listVpsOsTemplates(instanceId=" + instanceId + ") to resolve the current Ubuntu 24.04 "
                                + "template id. 2) buildVpsInstance with that id (confirm via executeConfirmedAction). "
                                + "3) Call osirAppMoveToOwned again with the same arguments to resume.");
            }
            if (System.currentTimeMillis() >= deadline) {
                return new MoveToOwnedResult(false, "BUILDING",
                        "VPS '" + instanceId + "' is still building (" + (buildState == null ? "status pending" : buildState)
                                + "). The order is placed — nothing more will be charged.",
                        instanceId, instance == null ? null : instance.getIpAddress(), domain, null,
                        "Call osirAppMoveToOwned again with the same arguments in a minute — it resumes and never orders twice.");
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
        // just bounce off C2's 422 — wait instead, the tracker keeps the move resumable.
        if (ip == null || ip.isBlank()) {
            return new MoveToOwnedResult(false, "BUILDING",
                    "VPS '" + instanceId + "' is built but has no IP address assigned yet. "
                            + "The order is placed — nothing more will be charged.",
                    instanceId, null, domain, null,
                    "Call osirAppMoveToOwned again with the same arguments in a minute — it resumes and never orders twice.");
        }
        if (!deploymentService.moveToOwned(appName, instanceId, ip, domain)) {
            // Tracker entry kept; C2's endpoint is idempotent per instanceId, so resuming is safe.
            return new MoveToOwnedResult(false, "FAILED",
                    "The server is ready but the platform could not ship the app onto it. "
                            + "Nothing more will be charged.",
                    instanceId, ip, domain, null,
                    "Call osirAppMoveToOwned again with the same arguments to retry the ship step.");
        }

        Boolean dnsBound = null;
        // C2 accepted the move but ships asynchronously (~3-5 min) — the honest instruction is
        // "poll osirAppStatus until tier reads owned", not "it's done".
        String nextStep = "Check osirAppStatus for '" + appName
                + "' until tier reads 'owned' (typically a few minutes).";
        if (domain != null && !domain.isBlank()) {
            dnsBound = bindDomain(domain, ip);
            if (!dnsBound) {
                nextStep = "The domain '" + domain + "' is not hosted on osir.app nameservers. Point an A record "
                        + "for it to " + ip + " at your DNS provider (add an AAAA record too if you use IPv6). "
                        + nextStep;
            }
        }

        // Same key the operation started with — never re-derive identity mid-flight.
        orderedInstances.remove(moveKey);
        LOG.infof("moveToOwned: app %s handed to C2 for instance %s (%s)", appName, instanceId, ip);
        return new MoveToOwnedResult(true, "MOVING",
                "The platform is now moving app '" + appName + "' onto your VPS '" + instanceId + "' (" + ip
                        + "). This takes a few minutes; the move is complete when osirAppStatus shows tier 'owned'. "
                        + "The shared-tier copy keeps running until you remove it.",
                instanceId, ip, domain, dnsBound, nextStep);
    }

    /**
     * Bind the apex A record to the new box when the zone is hosted with us. ALWAYS re-lists for
     * the current record id right before updating — ids are content-derived, a stale id is a 500.
     */
    private boolean bindDomain(String domain, String ip) {
        DnsRecordListResult zone = dnsService.listRecords(domain);
        if (!zone.isSuccess() || zone.getRecords() == null) {
            return false; // external NS (or zone unreadable) — caller returns manual instructions
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
     * Money-rule tracker key: the caller's stable subject + app. FAIL-CLOSED — if the subject
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
                    + "Re-authenticate (loginWithDevice or reconnect) and try again — nothing was ordered.");
        }
        return sub + "|" + appName;
    }
}
