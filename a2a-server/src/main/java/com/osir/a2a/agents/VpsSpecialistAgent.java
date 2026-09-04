package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.VpsService;
import com.osir.a2a.security.ConfirmationGate;
import com.osir.mcp.security.DestructiveOpRateLimiter.Bucket;
import com.osir.mcp.services.CatalogService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class VpsSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(VpsSpecialistAgent.class);

    @Inject VpsService vpsService;
    @Inject CatalogService catalogService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "vps-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("list_vps_packages", "list_vps_locations", "order_vps",
                "list_vps_instances", "get_vps_details", "delete_vps", "vps_panel_login", "get_catalog",
                "list_os_templates", "build_vps", "list_ssh_keys", "add_ssh_key",
                "get_vps_package_details", "count_vps_instances", "get_dedicated_catalog",
                ConfirmationGate.CONFIRM_SKILL);
    }

    @Override
    protected Set<String> getKeywords() {
        // No "os" or "key": score() matches bare substrings, so "os" hits cost/host/most/close (and this
        // agent's own "hosting") and "key" hits domain auth-key questions. "ssh"/"template"/"reinstall"/
        // "rebuild" are specific enough to route OS work here.
        return Set.of("vps", "server", "virtual", "hosting", "instance", "package", "provision", "catalog",
                "datacenter", "location", "template", "reinstall", "rebuild", "ssh");
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            // An explicit skill is a routing decision the registry already made on a 1.0 score; the text
            // matching below must not second-guess it. Every branch is `skillEquals || textContains`, so
            // otherwise skill=delete_vps with "terminate it, I lost the ssh key" would hit the bare
            // contains("ssh") branch and list keys instead of terminating.
            String lower = skill != null ? "" : text.toLowerCase();

            // FIRST, before anything text-matched: a message carrying an actionId is confirming an
            // action already staged on this task. Otherwise "yes, terminate it" re-enters the
            // delete branch below on a bare contains() and stages a SECOND delete.
            if (isConfirming(task)) {
                return runConfirmed(task);
            }

            // Read-only lookups, explicit-skill only: their words ("details", "how many", "catalog")
            // overlap the branches below, and a wrong guess there costs a server.
            if (skill != null) {
                switch (skill) {
                    case "get_vps_package_details": return handlePackageDetails(task);
                    case "count_vps_instances": return handleCountInstances(task);
                    case "get_dedicated_catalog": return handleDedicatedCatalog(task);
                    default: break;
                }
            }

            // These branches come first on purpose: the generic ones below match on bare "list" and
            // "create", so an OS/SSH request would otherwise be swallowed by list_vps_instances or
            // order_vps before it ever reached here.
            // "template" anywhere in the text means the caller is asking what is *available*, not asking
            // to install something: "which templates can I reinstall this server with?" contains
            // "reinstall" and would otherwise land here and answer a read-only question with an
            // ERASES-ALL-DATA prompt. Listing is free and reversible, a build wipes a disk, so an
            // ambiguous phrase has to resolve to the listing. An explicit skill=build_vps still builds.
            if ("build_vps".equals(skill) || (!lower.contains("template")
                    && (lower.contains("reinstall") || lower.contains("rebuild")
                        || lower.contains("install os")))) {
                String instanceId = meta(task, "instanceId");
                Integer operatingSystemId = metaInt(task, "operatingSystemId");
                if (instanceId == null || operatingSystemId == null) {
                    return askForInput(task,
                            "To install an operating system, please provide in metadata: instanceId, operatingSystemId "
                            + "(use 'list OS templates' with instanceId to resolve one). Optional: sshKeyIds "
                            + "(comma-separated), hostname, swap. WARNING: on a server that already has an OS this "
                            + "ERASES ALL DATA on it, including any deployed application, and cannot be undone.");
                }
                // Staged, not run: the parameters are frozen on the task now and the confirm message
                // carries only the actionId, so the caller cannot re-send different ones. (The old
                // in-band confirm=ERASE token needed the whole request repeated, which dropped
                // metadata, because a continuation replaces it rather than merging.)
                Map<String, Object> params = new java.util.LinkedHashMap<>();
                params.put("instanceId", instanceId);
                params.put("operatingSystemId", operatingSystemId);
                putIfPresent(params, "hostname", meta(task, "hostname"));
                putIfPresent(params, "sshKeyIds", meta(task, "sshKeyIds"));
                putIfPresent(params, "swap", metaDouble(task, "swap"));
                return stage(task, "build_vps", params,
                        "Install an operating system on VPS '" + instanceId + "' (template "
                                + operatingSystemId + "). This ERASES ALL DATA on that server, including any "
                                + "deployed application, and cannot be undone.",
                        Bucket.DESTRUCTIVE);
            } else if ("list_os_templates".equals(skill) || lower.contains("template")
                    || lower.contains("operating system")) {
                String templatePackageId = meta(task, "packageId");
                String templateInstanceId = meta(task, "instanceId");
                if (templatePackageId == null && templateInstanceId == null) {
                    return askForInput(task,
                            "To list OS templates, please provide either packageId or instanceId in metadata. "
                            + "Use packageId to see what a package can install before ordering, or instanceId to "
                            + "reinstall an existing server. Ids change over time, so they must be looked up fresh.");
                }
                var result = vpsService.listOsTemplates(templatePackageId, templateInstanceId,
                        metaBool(task, "includeEol"));
                return completeWithResult(task, "vps-os-templates", result, result.isSuccess(),
                        result.isSuccess() ? "OS templates retrieved." : result.getMessage());
            } else if ("add_ssh_key".equals(skill) || (lower.contains("ssh")
                    && (lower.contains("add") || lower.contains("store") || lower.contains("upload")))) {
                String name = meta(task, "name");
                String publicKey = meta(task, "publicKey");
                if (name == null || publicKey == null) {
                    return askForInput(task,
                            "To store an SSH key, please provide in metadata: name (a label, e.g. 'laptop') and "
                            + "publicKey (a single-line OpenSSH public key). Storing a key you already have is a "
                            + "no-op — it returns the existing one.");
                }
                var result = vpsService.storeSshKey(name, publicKey);
                // The success message already says whether it was stored or already present.
                return completeWithResult(task, "vps-ssh-key", result, result.isSuccess(), result.getMessage());
            } else if ("list_ssh_keys".equals(skill) || lower.contains("ssh")) {
                var result = vpsService.listSshKeys();
                return completeWithResult(task, "vps-ssh-keys", result, result.isSuccess(),
                        result.isSuccess() ? "SSH keys retrieved." : result.getMessage());
            } else if ("list_vps_packages".equals(skill) || lower.contains("package") || lower.contains("plan")) {
                var result = vpsService.listPackages();
                return completeWithResult(task, "vps-packages", result, result.isSuccess(),
                        result.isSuccess() ? "VPS packages retrieved." : result.getMessage());
            } else if ("list_vps_locations".equals(skill) || lower.contains("location") || lower.contains("datacenter")) {
                var result = vpsService.listLocations();
                return completeWithResult(task, "vps-locations", result, result.isSuccess(),
                        result.isSuccess() ? "VPS locations retrieved." : result.getMessage());
            } else if ("list_vps_instances".equals(skill) || lower.contains("my") || lower.contains("list") || lower.contains("instance")) {
                var result = vpsService.listMyInstances();
                return completeWithResult(task, "vps-instances", result, result.isSuccess(),
                        result.isSuccess() ? "Your VPS instances retrieved." : result.getMessage());
            } else if ("order_vps".equals(skill) || lower.contains("order") || lower.contains("provision") || lower.contains("create")) {
                String packageId = meta(task, "packageId");
                String hostname = meta(task, "hostname");
                String paymentTerm = meta(task, "paymentTerm");
                if (packageId == null || hostname == null || paymentTerm == null) {
                    return askForInput(task,
                            "To order a VPS, please provide in metadata: packageId, hostname, paymentTerm " +
                            "(MONTHLY/SEMI_ANNUAL/ANNUAL/BIENNIAL/TRIENNIAL). " +
                            "Optional: operatingSystemId (integer template id — without it the server is created " +
                            "with no operating system), sshKeyIds (comma-separated key ids). " +
                            "Use 'list VPS packages' to see available options.");
                }
                Map<String, Object> params = new java.util.LinkedHashMap<>();
                params.put("packageId", packageId);
                params.put("hostname", hostname);
                params.put("paymentTerm", paymentTerm);
                putIfPresent(params, "operatingSystemId", metaInt(task, "operatingSystemId"));
                putIfPresent(params, "sshKeyIds", meta(task, "sshKeyIds"));
                return stage(task, "order_vps", params,
                        "Order a VPS: package '" + packageId + "', hostname '" + hostname + "', term "
                                + paymentTerm + ". This DEDUCTS FROM THE ACCOUNT BALANCE and starts a "
                                + "recurring charge.",
                        Bucket.FINANCIAL);
            } else if ("get_vps_details".equals(skill)) {
                String instanceId = meta(task, "instanceId");
                if (instanceId == null) return askForInput(task, "Please provide the VPS instance ID in metadata.");
                var result = vpsService.getInstanceDetails(instanceId);
                return completeWithResult(task, "vps-details", result, result.isSuccess(),
                        result.isSuccess() ? "VPS details retrieved." : result.getMessage());
            } else if ("delete_vps".equals(skill) || lower.contains("delete") || lower.contains("terminate")) {
                String instanceId = meta(task, "instanceId");
                if (instanceId == null) return askForInput(task, "Please provide the VPS instance ID to terminate.");
                return stage(task, "delete_vps", Map.of("instanceId", instanceId),
                        "Terminate VPS '" + instanceId + "'. This DESTROYS the server and everything on it, "
                                + "cannot be undone, and does not refund the remaining term.",
                        Bucket.DESTRUCTIVE);
            } else if ("get_catalog".equals(skill) || lower.contains("catalog")) {
                var result = catalogService.getProductCatalog();
                return completeWithResult(task, "catalog", result, result.isSuccess(),
                        result.isSuccess() ? "Product catalog retrieved." : result.getMessage());
            } else if ("vps_panel_login".equals(skill) || lower.contains("panel") || lower.contains("login")) {
                String instanceId = meta(task, "instanceId");
                if (instanceId == null) return askForInput(task, "Please provide the VPS instance ID to get the panel login URL.");
                var result = vpsService.loginToPanel(instanceId);
                return completeWithResult(task, "panel-login", result, result.isSuccess(),
                        result.isSuccess() ? "Panel login URL retrieved." : result.getMessage());
            } else {
                var result = vpsService.listPackages();
                return completeWithResult(task, "vps-packages", result, result.isSuccess(), "Here are the available VPS packages.");
            }
        } catch (Exception e) {
            LOG.errorf(e, "VPS agent error: %s", e.getMessage());
            return failWithError(task, e.getMessage());
        }
    }

    /**
     * Run what was staged, from the parameters frozen at stage time — never from the confirm
     * message, which carries only the actionId.
     */
    private A2ATask runConfirmed(A2ATask task) {
        var claim = confirmationGate.claim(task);
        if (!claim.ok()) {
            return failWithError(task, claim.error());
        }
        Map<String, Object> p = claim.action().params();
        switch (claim.action().skill()) {
            case "order_vps" -> {
                var result = vpsService.orderVps(str(p, "packageId"), str(p, "hostname"), str(p, "paymentTerm"),
                        integer(p, "operatingSystemId"), parseIds(str(p, "sshKeyIds")));
                return completeWithResult(task, "vps-order", result, result.isSuccess(),
                        result.isSuccess() ? "VPS ordered successfully." : result.getMessage());
            }
            case "delete_vps" -> {
                var result = vpsService.deleteInstance(str(p, "instanceId"));
                return completeWithResult(task, "vps-delete", result, result.isSuccess(),
                        result.isSuccess() ? "VPS instance terminated." : result.getMessage());
            }
            case "build_vps" -> {
                Double swap = p.get("swap") instanceof Number n ? n.doubleValue() : null;
                var result = vpsService.buildInstance(str(p, "instanceId"), integer(p, "operatingSystemId"),
                        str(p, "hostname"), parseIds(str(p, "sshKeyIds")), swap);
                return completeWithResult(task, "vps-build", result, result.isSuccess(),
                        result.isSuccess() ? "OS install queued. Poll 'get VPS details' until buildState is COMPLETE."
                                : result.getMessage());
            }
            default -> {
                return failWithError(task, "Cannot run '" + claim.action().skill() + "': unknown staged action.");
            }
        }
    }

    private static void putIfPresent(Map<String, Object> params, String key, Object value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    private static String str(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v == null ? null : v.toString();
    }

    private static Integer integer(Map<String, Object> params, String key) {
        Object v = params.get(key);
        return v instanceof Number n ? n.intValue() : null;
    }

    private A2ATask handlePackageDetails(A2ATask task) {
        String packageId = meta(task, "packageId");
        if (packageId == null) {
            return askForInput(task,
                    "To get package details, please provide in metadata: packageId "
                    + "(use the list_vps_packages skill to see the ids).");
        }
        var result = vpsService.getPackageDetails(packageId);
        return completeWithResult(task, "vps-package", result, result.isSuccess(),
                result.isSuccess() ? "VPS package details retrieved." : result.getMessage());
    }

    private A2ATask handleCountInstances(A2ATask task) {
        var result = vpsService.countMyInstances();
        return completeWithResult(task, "vps-count", result, result.isSuccess(),
                result.isSuccess() ? "VPS instance count retrieved." : result.getMessage());
    }

    private A2ATask handleDedicatedCatalog(A2ATask task) {
        var result = catalogService.getDedicatedServerCatalog();
        return completeWithResult(task, "dedicated-catalog", result, result.isSuccess(),
                result.isSuccess() ? "Dedicated server catalog retrieved." : result.getMessage());
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR VPS & Infrastructure Agent");
        card.setDescription("Manages VPS hosting, server provisioning, and product catalog.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("list_vps_packages", "List VPS Packages", "List available VPS hosting packages",
                        List.of("vps", "packages", "plans"),
                        List.of("What VPS plans do you offer?", "Show me your server packages and prices")),
                new Skill("list_vps_locations", "List VPS Locations", "List available datacenter locations",
                        List.of("vps", "locations", "datacenter"),
                        List.of("Which datacenter locations are available?", "Can I get a server in Frankfurt?")),
                new Skill("order_vps", "Order VPS", "Provision a new VPS instance",
                        List.of("vps", "order", "provision"),
                        List.of("Order a small VPS with hostname web01.cedarloop.com on monthly billing",
                                "Spin up a new server for me")),
                new Skill("list_vps_instances", "List VPS Instances", "List your active VPS instances",
                        List.of("vps", "instances", "list"),
                        List.of("List my VPS instances", "What servers do I have running?")),
                new Skill("get_vps_details", "Get VPS Details", "Get details of a specific VPS instance",
                        List.of("vps", "details", "instance"),
                        List.of("Show me the details of my server web01", "What is the IP of VPS instance 4821?")),
                new Skill("delete_vps", "Delete VPS", "Terminate a VPS instance",
                        List.of("vps", "delete", "terminate"),
                        List.of("Terminate VPS instance 4821", "Delete my old test server")),
                new Skill("vps_panel_login", "VPS Panel Login", "Get control panel login URL",
                        List.of("vps", "panel", "login"),
                        List.of("Give me the control panel login for web01", "How do I log into my VPS panel?")),
                new Skill("get_catalog", "Get Product Catalog", "Get the full product catalog",
                        List.of("vps", "catalog", "products"),
                        List.of("Show me the full product catalog", "What products does OSIR sell?")),
                new Skill("list_os_templates", "List OS Templates",
                        "List operating systems installable on a VPS instance",
                        List.of("vps", "os-templates", "operating-system"),
                        List.of("Which OS templates can I install on instance 4821?",
                                "What operating systems are available for the medium package?")),
                new Skill("build_vps", "Install VPS Operating System",
                        "Install an OS on a VPS instance. DESTRUCTIVE - erases all data on the server; "
                        + "requires metadata confirm=ERASE",
                        List.of("vps", "install", "rebuild", "destructive"),
                        List.of("Reinstall Ubuntu 24.04 on instance 4821", "Rebuild my server with Debian 12")),
                new Skill("list_ssh_keys", "List SSH Keys", "List the SSH keys stored on your account",
                        List.of("vps", "ssh", "keys"),
                        List.of("List my SSH keys", "Which SSH keys are stored on my account?")),
                new Skill("add_ssh_key", "Add SSH Key", "Store an SSH public key for use in VPS installs",
                        List.of("vps", "ssh", "add-key"),
                        List.of("Add my laptop's SSH key: ssh-ed25519 AAAAC3Nz... armand@laptop",
                                "Store a new SSH public key on my account")),
                new Skill("get_vps_package_details", "Get VPS Package Details",
                        "Full specs and price for one VPS package",
                        List.of("vps", "packages", "details"),
                        List.of("What exactly do I get with OSIR-S-US?",
                                "How much RAM and disk does that package have?")),
                new Skill("count_vps_instances", "Count VPS Instances",
                        "How many servers the account currently has",
                        List.of("vps", "instances", "count"),
                        List.of("How many servers do I have?", "Am I close to my server limit?")),
                new Skill(ConfirmationGate.CONFIRM_SKILL, "Confirm A Staged Action",
                        "Run an action this agent staged: send the actionId it returned, on the same task",
                        List.of("confirmation", "safety"),
                        List.of("Confirm action a2a_1f4c... on this task",
                                "Yes, go ahead with the order you summarised")),
                new Skill("get_dedicated_catalog", "Get Dedicated Server Catalog",
                        "Dedicated (bare metal) server configurations and prices",
                        List.of("dedicated", "bare-metal", "catalog"),
                        List.of("Do you offer dedicated servers?",
                                "What bare metal configurations can I buy?"))
        ));
        return card;
    }

    // metaInt / metaDouble come from BaseSpecialistAgent; only these two are new.

    private Boolean metaBool(A2ATask task, String key) {
        String raw = meta(task, key);
        return raw == null || raw.isBlank() ? null : Boolean.valueOf(raw.trim());
    }

    /** Comma-separated ids, e.g. "3,7". Returns null when absent so the backend sees no key list. */
    private List<Integer> metaIntList(A2ATask task, String key) {
        return parseIds(meta(task, key));
    }

    /** Same parsing for a value read back from a staged action's frozen parameters. */
    private static List<Integer> parseIds(String raw) {
        if (raw == null || raw.isBlank()) return null;
        List<Integer> ids = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            try {
                ids.add(Integer.valueOf(part.trim()));
            } catch (NumberFormatException e) {
                // Skip junk rather than fail: the backend rejects unowned/unknown ids anyway.
            }
        }
        return ids.isEmpty() ? null : ids;
    }
}
