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
                "list_vps_instances", "get_vps_details", "delete_vps", "vps_panel_login", "get_catalog");
    }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("vps", "server", "virtual", "hosting", "instance", "package", "provision", "catalog", "datacenter", "location");
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            if ("list_vps_packages".equals(skill) || lower.contains("package") || lower.contains("plan")) {
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
                            "To order a VPS, please provide in metadata: packageId, hostname, paymentTerm (monthly/quarterly/annually). " +
                            "Optional: operatingSystem. Use 'list VPS packages' to see available options.");
                }
                var result = vpsService.orderVps(packageId, hostname, paymentTerm, meta(task, "operatingSystem"));
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
                new Skill("list_vps_packages", "List VPS Packages", "List available VPS hosting packages"),
                new Skill("list_vps_locations", "List VPS Locations", "List available datacenter locations"),
                new Skill("order_vps", "Order VPS", "Provision a new VPS instance"),
                new Skill("list_vps_instances", "List VPS Instances", "List your active VPS instances"),
                new Skill("get_vps_details", "Get VPS Details", "Get details of a specific VPS instance"),
                new Skill("delete_vps", "Delete VPS", "Terminate a VPS instance"),
                new Skill("vps_panel_login", "VPS Panel Login", "Get control panel login URL"),
                new Skill("get_catalog", "Get Product Catalog", "Get the full product catalog")
        ));
        return card;
    }
}
