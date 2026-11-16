package com.osir.mcp;

import com.osir.mcp.models.deploy.AgentDtos.AgentActionResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentDeployResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentListResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.AgentService;
import com.osir.mcp.services.DeploymentService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Ready-made AI agents (Hermes) installed on the user's OWN VPS. A separate product from osirApp*:
 * the user never sees or edits the agent's code, only its settings. MCP-only on purpose — A2A has no
 * confirmation step for spending/deleting, and the one-time password must reach the user, not another
 * agent (howto_agents.md M5).
 */
@McpAudited
@ApplicationScoped
public class AgentMCPServer {

    @Inject
    AgentService agentService;

    @Inject
    DeploymentService deploymentService;

    @Inject
    PendingActionStore pendingActionStore;

    @RequiresAuth
    @Tool(name = "osirAgentDeploy",
            description = "osirAgentDeploy: Install a ready-made AI agent - Hermes (Hermes Agent by Nous Research, a "
                    + "self-hosted personal AI agent with a web dashboard) - on the user's OWN VPS, behind HTTPS with a "
                    + "password-protected dashboard. Use this for 'run/install Hermes on my server' INSTEAD of osirAppDeploy, "
                    + "a hand-written Dockerfile, SSH or an install script: the platform installs the vetted agent itself. "
                    + "Needs a VPS with at least 4 GB RAM. TWO WAYS IN, like osirAppDeployToVps: (1) the user already owns a "
                    + "VPS: pass instanceId (from listMyVpsInstances) - spends nothing, no confirmation; (2) no server yet: "
                    + "pass packageId (from listVpsPackages, 4 GB+ RAM) - stages a VPS order (COSTS MONEY): returns an "
                    + "actionId; present the price/summary and call executeConfirmedAction only if the user approves. "
                    + "The result carries the dashboard URL, username and the PASSWORD: show the password to the user once, "
                    + "in that reply - it is never shown again (osirAgentResetPassword makes a new one). Then poll "
                    + "osirAgentStatus until installed and connect a model provider with osirAgentConfigure. Calling this "
                    + "again resumes an install and never buys a second VPS. If the platform says the agent is not "
                    + "available yet, tell the user plainly. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Install an AI agent on the user's VPS",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public Object osirAgentDeploy(
            @ToolArg(description = "The agent to install. Currently only 'hermes'.") String agent,
            @ToolArg(required = false, description = "Id of a VPS the user ALREADY owns, from listMyVpsInstances. Nothing is ordered or charged. Never invent one.") String instanceId,
            @ToolArg(required = false, description = "VPS package id from listVpsPackages (at least 4 GB RAM). Only when a server has to be ordered; omit it when passing instanceId.") String packageId,
            @ToolArg(required = false, description = "Domain to serve the dashboard on, e.g. 'agent.example.com'. Bound automatically if its DNS is hosted at Osir, otherwise the result says which A record to add.") String domain,
            @ToolArg(required = false, description = "Dashboard login name; default 'admin'.") String username,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            String unknown = agentService.unknownAgent(agent);
            if (unknown != null) {
                return AgentDeployResult.fail(unknown);
            }
            Object done = agentService.deployWithoutSpending(agent, instanceId, domain, username);
            if (done != null) {
                return done;
            }
            if (packageId == null || packageId.isBlank()) {
                return AgentDeployResult.fail("There is no server to install " + agent + " on yet. Either pass "
                        + "instanceId of a VPS the user already owns with at least " + AgentService.MIN_MEMORY_MB
                        + " MB RAM (listMyVpsInstances), or pass packageId to order one (listVpsPackages) - ordering "
                        + "costs money and needs the user's confirmation.");
            }
            AgentService.OrderPlan plan = agentService.planOrder(agent, packageId);
            return pendingActionStore.stage(
                    "osirAgentDeploy",
                    "Order a VPS (package '" + packageId + "', " + plan.prep().osDisplayName() + ", monthly) and install "
                            + plan.agent() + " on it, deducts from account balance",
                    connection.id(),
                    DestructiveOpRateLimiter.Bucket.FINANCIAL,
                    () -> agentService.orderAndInstall(plan, packageId, domain, username));
        } catch (IllegalStateException e) {
            return AgentDeployResult.fail(e.getMessage());
        } catch (Exception e) {
            Log.errorf("osirAgentDeploy error: %s", e.getClass().getSimpleName());   // never the result: password
            return AgentDeployResult.fail("Could not start the install right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentList",
            description = "osirAgentList: List the AI agents (e.g. Hermes) the user has installed on their own VPS, "
                    + "with their ids and dashboard URLs. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List installed AI agents",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AgentListResult osirAgentList(
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return agentService.list();
        } catch (Exception e) {
            Log.errorf("osirAgentList error: %s", e.getClass().getSimpleName());
            return AgentListResult.fail("Could not list agents right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentStatus",
            description = "osirAgentStatus: Is the user's AI agent (e.g. Hermes) installed and working? Reports install "
                    + "progress, the dashboard URL, and whether the dashboard asks for a login (dashboardProtected - checked "
                    + "live). If an install failed, the message says why and what to do. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get AI agent status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = true))
    public AgentStatusResult osirAgentStatus(
            @ToolArg(description = "Agent id from osirAgentList or osirAgentDeploy.") String agentId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return agentService.status(agentId);
        } catch (Exception e) {
            Log.errorf("osirAgentStatus error for %s: %s", agentId, e.getClass().getSimpleName());
            return AgentStatusResult.fail("Could not get the agent's status right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentConfigure",
            description = "osirAgentConfigure: Connect the user's AI agent (e.g. Hermes) to a model provider - "
                    + "'connect my Hermes to DeepSeek'. Providers: deepseek, openai, openrouter, anthropic. The API key is "
                    + "stored as an encrypted secret, never written into a file or shown again, and the agent restarts on "
                    + "the user's VPS with it. Never paste the key anywhere else. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Connect an AI agent to a model provider",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AgentActionResult osirAgentConfigure(
            @ToolArg(description = "Agent id from osirAgentList or osirAgentDeploy.") String agentId,
            @ToolArg(description = "Model provider: 'deepseek', 'openai', 'openrouter' or 'anthropic'.") String provider,
            @ToolArg(description = "The user's API key for that provider. Never logged or returned.") String apiKey,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return agentService.configure(agentId, provider, apiKey);
        } catch (Exception e) {
            Log.errorf("osirAgentConfigure error for %s: %s", agentId, e.getClass().getSimpleName());   // never the key
            return AgentActionResult.fail("Could not connect the provider right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentLogs",
            description = "osirAgentLogs: Logs of the user's AI agent (e.g. Hermes). Not available yet - the result says "
                    + "so; then use osirAgentStatus. Never invent log contents or ask the user for SSH access. "
                    + "Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get AI agent logs",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AgentActionResult osirAgentLogs(
            @ToolArg(description = "Agent id from osirAgentList or osirAgentDeploy.") String agentId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return agentService.logs(agentId);
        } catch (Exception e) {
            Log.errorf("osirAgentLogs error for %s: %s", agentId, e.getClass().getSimpleName());
            return AgentActionResult.fail("Could not reach the platform right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentResetPassword",
            description = "osirAgentResetPassword: Set a new dashboard password for the user's AI agent (e.g. Hermes) - "
                    + "'I lost the password'. The old one stops working. The new password is in this result only: show it "
                    + "to the user once, in that reply. This is the ONLY way to change the agent's login. "
                    + "Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Reset AI agent dashboard password",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public AgentActionResult osirAgentResetPassword(
            @ToolArg(description = "Agent id from osirAgentList or osirAgentDeploy.") String agentId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            return agentService.resetPassword(agentId);
        } catch (Exception e) {
            Log.errorf("osirAgentResetPassword error for %s: %s", agentId, e.getClass().getSimpleName());
            return AgentActionResult.fail("Could not reset the password right now. Please try again.");
        }
    }

    @RequiresAuth
    @Tool(name = "osirAgentDelete",
            description = "osirAgentDelete: Stage removal of the user's AI agent (e.g. Hermes). DESTRUCTIVE: stops and "
                    + "removes the agent from the user's VPS. Its data folder (conversations, memories) stays on the VPS, "
                    + "and the VPS itself is NOT cancelled (that is deleteVpsInstance). Returns an actionId; present the "
                    + "summary and call executeConfirmedAction only if the user approves. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Remove an AI agent",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false,
                    openWorldHint = false))
    public Object osirAgentDelete(
            @ToolArg(description = "Agent id from osirAgentList.") String agentId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        try {
            String notAgent = agentService.requireAgent(agentId);
            if (notAgent != null) {
                return AgentActionResult.fail(notAgent);
            }
            AppDto app = agentService.agentApp(agentId);
            String label = app == null ? agentId : "'" + app.name() + "'" + (app.catalog() == null ? "" : " (" + app.catalog() + ")");
            return pendingActionStore.stage(
                    "osirAgentDelete",
                    "Remove agent " + label + " from the user's VPS. Its data folder stays on the VPS; the VPS is not cancelled",
                    connection.id(),
                    DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                    () -> deploymentService.delete(agentId));
        } catch (Exception e) {
            Log.errorf("osirAgentDelete error for %s: %s", agentId, e.getClass().getSimpleName());
            return AgentActionResult.fail("Could not stage the removal right now. Please try again.");
        }
    }
}
