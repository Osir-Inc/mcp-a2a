package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.DeploymentService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

/**
 * Specialist agent for app and site deployment on the OSIR platform.
 *
 * <p>Covers the safe core only: listing apps, status, logs, and triggering a deploy
 * (app builds are free on OSIR, so deploy is not billable). Destructive or financial
 * multi-step operations (delete, move-to-owned) are deliberately not exposed here.
 */
@ApplicationScoped
public class DeploySpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(DeploySpecialistAgent.class);

    @Inject DeploymentService deploymentService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "deploy-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("list_apps", "get_app_status", "get_app_logs", "deploy_app");
    }

    @Override
    protected Set<String> getKeywords() {
        // No bare "app" or "logs": score() matches bare substrings, so "app" hits happen/apply
        // and "logs" collides with the account agent's audit "log". "hosting" belongs to the VPS
        // agent. "deploy"/"publish"/"website" are distinctive for this agent.
        return Set.of("deploy", "publish", "website", "web app", "my apps", "app status", "app logs", "site");
    }

    @Override
    protected double getKeywordWeight() { return 0.3; }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String lower = getLatestUserMessage(task).toLowerCase();

            // Explicit skill wins over text keywords so free text cannot hijack the routing.
            if (skill != null) {
                switch (skill) {
                    case "deploy_app": return handleDeployApp(task);
                    case "get_app_status": return handleGetStatus(task);
                    case "get_app_logs": return handleGetLogs(task);
                    case "list_apps": return handleListApps(task);
                    default: break;
                }
            }
            if (lower.contains("deploy") || lower.contains("publish")) {
                return handleDeployApp(task);
            } else if (lower.contains("log")) {
                return handleGetLogs(task);
            } else if (lower.contains("status") || lower.contains("health")) {
                return handleGetStatus(task);
            } else {
                return handleListApps(task);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Deploy agent error: %s", e.getMessage());
            return failWithException(task, e);
        }
    }

    private A2ATask handleListApps(A2ATask task) {
        var result = deploymentService.listApps();
        return completeWithResult(task, "app-list", result, result.success(),
                result.success() ? "Apps retrieved." : result.message());
    }

    private A2ATask handleGetStatus(A2ATask task) {
        String appId = appIdFrom(task);
        if (appId == null) {
            return askForInput(task,
                    "To get an app's status, please provide in metadata: appId (or name). " +
                    "Use the list_apps skill to see your deployed apps.");
        }
        var result = deploymentService.getStatus(appId);
        return completeWithResult(task, "app-status", result, result.success(),
                result.success() ? "App status retrieved." : result.message());
    }

    private A2ATask handleGetLogs(A2ATask task) {
        String appId = appIdFrom(task);
        if (appId == null) {
            return askForInput(task,
                    "To fetch app logs, please provide in metadata: appId (or name). Optional: tail (number of lines).");
        }
        var result = deploymentService.getLogs(appId, metaInt(task, "tail"));
        return completeWithResult(task, "app-logs", result, result.success(),
                result.success() ? "App logs retrieved." : result.message());
    }

    private A2ATask handleDeployApp(A2ATask task) {
        String name = meta(task, "name");
        String language = meta(task, "language");
        String region = meta(task, "region");
        String uploadTicket = meta(task, "uploadTicket");

        if (name == null || language == null) {
            return askForInput(task,
                    "To deploy an app, please provide in metadata: name, language (e.g. node, python, java). " +
                    "Optional: region, uploadTicket (from a prior source upload; omit it to redeploy the app's current source).");
        }
        var result = deploymentService.deploy(name, language, region, uploadTicket);
        return completeWithResult(task, "deploy-result", result, result.success(), result.message());
    }

    /** App identifier from metadata: appId preferred, name accepted as a fallback. */
    private String appIdFrom(A2ATask task) {
        String appId = meta(task, "appId");
        return appId != null ? appId : meta(task, "name");
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Deploy Agent");
        card.setDescription("Deploys and monitors sites and apps on the OSIR platform: list deployed apps, check status and health, fetch logs, and trigger deployments.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("list_apps", "List Apps", "List the apps deployed on your OSIR account",
                        List.of("apps", "deploy", "list"),
                        List.of("Show me my deployed apps", "What apps do I have running?")),
                new Skill("get_app_status", "Get App Status", "Get the status, health, and live URL of a deployed app (appId or name via metadata)",
                        List.of("app", "status", "health"),
                        List.of("What is the status of my blog app?", "Is my-shop healthy?")),
                new Skill("get_app_logs", "Get App Logs", "Fetch recent logs for a deployed app (appId or name via metadata, optional tail)",
                        List.of("app", "logs", "debug"),
                        List.of("Show me the logs for my-api", "Why is my app failing? Check the logs")),
                new Skill("deploy_app", "Deploy App", "Trigger a deployment of an app; provide name and language, plus an optional uploadTicket for new source",
                        List.of("deploy", "publish", "app", "site"),
                        List.of("Deploy my node app called my-shop", "Publish my website"))
        ));
        return card;
    }
}
