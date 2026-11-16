package com.osir.mcp.models.deploy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;

import java.util.List;

/**
 * Vetted agents (Hermes) on the customer's own VPS: C2's /v1/agents wire shapes (CONTRACTS.md §2) and
 * the osirAgent* tool results. An agent is a C2 app with kind "agent"; list/status/delete use /v1/apps.
 *
 * <p><b>The dashboard password</b> exists only in {@link AgentInstallEnvelope} (the call that created
 * the agent) and {@link PasswordEnvelope}. It goes to the user once, in the tool result, and nowhere
 * else: no record here has a toString that anything logs, and nothing stores it.
 */
public final class AgentDtos {
    private AgentDtos() {
    }

    // ---- C2 wire shapes --------------------------------------------------------------------------
    public record AgentInstallBody(String name, String agent, String instanceId, String ip, String domain,
                                   String username) {
    }

    /** 202 from POST /v1/agents. {@code password} is null on a retry that resumed an install. */
    public record AgentInstallEnvelope(AppDto app, String status, String username, String password) {
        @Override
        public String toString() {   // never let a stray log line print the password
            return "AgentInstallEnvelope[app=" + (app == null ? null : app.name()) + ", status=" + status + "]";
        }
    }

    public record ProviderBody(String provider, String apiKey) {
        @Override
        public String toString() {
            return "ProviderBody[provider=" + provider + "]";
        }
    }

    public record ProviderEnvelope(String status) {
    }

    public record PasswordEnvelope(String password) {
        @Override
        public String toString() {
            return "PasswordEnvelope[***]";
        }
    }

    // ---- Tool results ----------------------------------------------------------------------------
    /** osirAgentDeploy. {@code password} is present only when this call created the agent. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentDeployResult(boolean success, String status, String message, String agentId,
                                    String url, String username, String password, String instanceId,
                                    String ip, String domain, Boolean dnsBound, String nextStep) {
        public static AgentDeployResult fail(String msg) {
            return new AgentDeployResult(false, "FAILED", msg, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public String toString() {
            return "AgentDeployResult[status=" + status + ", agentId=" + agentId + "]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentListResult(boolean success, String message, List<AppDto> agents) {
        public static AgentListResult fail(String msg) {
            return new AgentListResult(false, msg, List.of());
        }
    }

    /**
     * osirAgentStatus. {@code installed}: the install finished (ownedMove MOVED). {@code dashboardProtected}:
     * an unauthenticated request to the URL answered 401 (null = could not check, e.g. not installed yet).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentStatusResult(boolean success, String message, String agentId, String agent, String url,
                                    Boolean installed, String installState, String installStage,
                                    Boolean dashboardProtected, OwnedMoveDto ownedMove) {
        public static AgentStatusResult fail(String msg) {
            return new AgentStatusResult(false, msg, null, null, null, null, null, null, null, null);
        }
    }

    /** osirAgentConfigure / osirAgentLogs / osirAgentResetPassword. {@code password} only on a reset. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AgentActionResult(boolean success, String message, String password, String nextStep) {
        public static AgentActionResult fail(String msg) {
            return new AgentActionResult(false, msg, null, null);
        }

        @Override
        public String toString() {
            return "AgentActionResult[success=" + success + "]";
        }
    }
}
