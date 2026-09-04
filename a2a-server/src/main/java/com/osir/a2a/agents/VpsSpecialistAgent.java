package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.VpsService;
import com.osir.mcp.services.CatalogService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
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
                "get_vps_package_details", "count_vps_instances", "get_dedicated_catalog");
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
                // A2A has no PendingActionStore - that lives in the MCP module - so this in-band token is
                // the confirmation gate. Without it an agent-to-agent call could wipe a live server with
                // no human ever seeing a warning.
                if (!"ERASE".equals(meta(task, "confirm"))) {
                    return askForInput(task,
                            "Installing an OS on instance '" + instanceId + "' ERASES ALL DATA on that server, "
                            + "including any deployed application, and cannot be undone. If the owner has agreed, "
                            + "resend the whole request - instanceId, operatingSystemId and any other metadata - "
                            + "with confirm=ERASE added. Continuation replaces the task's metadata rather than "
                            + "merging it, so sending confirm on its own drops the rest and lands back here.");
                }
                var result = vpsService.buildInstance(instanceId, operatingSystemId, meta(task, "hostname"),
                        metaIntList(task, "sshKeyIds"), metaDouble(task, "swap"));
                return completeWithResult(task, "vps-build", result, result.isSuccess(),
                        result.isSuccess() ? "OS install queued. Poll 'get VPS details' until buildState is COMPLETE."
                                : result.getMessage());
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
                var result = vpsService.orderVps(packageId, hostname, paymentTerm,
                        metaInt(task, "operatingSystemId"), metaIntList(task, "sshKeyIds"));
                return completeWithResult(task, "vps-order", result, result.isSuccess(),
                        result.isSuccess() ? "VPS ordered successfully." : result.getMessage());
            } else if ("get_vps_details".equals(skill)) {
                String instanceId = meta(task, "instanceId");
                if (instanceId == null) return askForInput(task, "Please provide the VPS instance ID in metadata.");
                var result = vpsService.getInstanceDetails(instanceId);
                return completeWithResult(task, "vps-details", result, result.isSuccess(),
                        result.isSuccess() ? "VPS details retrieved." : result.getMessage());
            } else if ("delete_vps".equals(skill) || lower.contains("delete") || lower.contains("terminate")) {
                String instanceId = meta(task, "instanceId");
                if (instanceId == null) return askForInput(task, "Please provide the VPS instance ID to terminate.");
                var result = vpsService.deleteInstance(instanceId);
                return completeWithResult(task, "vps-delete", result, result.isSuccess(),
                        result.isSuccess() ? "VPS instance terminated." : result.getMessage());
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
        String raw = meta(task, key);
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
