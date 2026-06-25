package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.AccountService;
import com.osir.mcp.services.AuditService;
import com.osir.mcp.services.AuthService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class AccountSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(AccountSpecialistAgent.class);

    @Inject AccountService accountService;
    @Inject AuditService auditService;
    @Inject AuthService authService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "account-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("get_profile", "get_account_summary", "get_auth_status",
                "get_audit_logs", "get_domain_audit", "get_recent_activity");
    }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("account", "profile", "summary", "audit", "activity", "log", "who am i", "my account", "status");
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            if ("get_profile".equals(skill) || lower.contains("profile") || lower.contains("who am i")) {
                var result = accountService.getMyProfile();
                return completeWithResult(task, "profile", result, result.isSuccess(),
                        result.isSuccess() ? "Profile retrieved." : result.getMessage());
            } else if ("get_account_summary".equals(skill) || lower.contains("summary") || lower.contains("overview")) {
                var result = accountService.getAccountSummary();
                return completeWithResult(task, "account-summary", result, result.isSuccess(),
                        result.isSuccess() ? "Account summary retrieved." : result.getMessage());
            } else if ("get_auth_status".equals(skill) || lower.contains("auth") || lower.contains("login status")) {
                var result = authService.getAuthStatus();
                task.addArtifact(Artifact.ofData("auth-status", toMap(result)));
                task.addMessage(new Message("agent", result.isAuthenticated()
                        ? "Authenticated as " + result.getUsername() : "Not authenticated."));
                task.transitionTo(TaskState.COMPLETED);
            } else if ("get_domain_audit".equals(skill) || (lower.contains("audit") && lower.contains("domain"))) {
                String domain = extractDomain(text);
                if (domain == null) return askForDomain(task, "view audit trail for");
                var result = auditService.getDomainAuditTrail(domain);
                return completeWithResult(task, "domain-audit", result, result.isSuccess(),
                        result.isSuccess() ? "Domain audit trail retrieved." : result.getMessage());
            } else if ("get_recent_activity".equals(skill) || lower.contains("recent") || lower.contains("activity")) {
                var result = auditService.getRecentActivity();
                return completeWithResult(task, "recent-activity", result, result.isSuccess(),
                        result.isSuccess() ? "Recent activity retrieved." : result.getMessage());
            } else if ("get_audit_logs".equals(skill) || lower.contains("audit") || lower.contains("log")) {
                var result = auditService.getMyAuditLogs(null, null);
                return completeWithResult(task, "audit-logs", result, result.isSuccess(),
                        result.isSuccess() ? "Audit logs retrieved." : result.getMessage());
            } else {
                var result = accountService.getAccountSummary();
                return completeWithResult(task, "account-summary", result, result.isSuccess(), "Account summary.");
            }
            return task;
        } catch (Exception e) {
            LOG.errorf(e, "Account agent error: %s", e.getMessage());
            return failWithException(task, e);
        }
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Account & Audit Agent");
        card.setDescription("Manages user account, profile, authentication status, and audit logs.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("get_profile", "Get Profile", "Get user profile information"),
                new Skill("get_account_summary", "Get Account Summary", "Get comprehensive account overview"),
                new Skill("get_auth_status", "Get Auth Status", "Check authentication status"),
                new Skill("get_audit_logs", "Get Audit Logs", "View audit logs"),
                new Skill("get_domain_audit", "Get Domain Audit", "View audit trail for a specific domain"),
                new Skill("get_recent_activity", "Get Recent Activity", "View recent activity across services")
        ));
        return card;
    }
}
