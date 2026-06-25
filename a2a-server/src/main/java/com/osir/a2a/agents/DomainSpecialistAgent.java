package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.models.*;
import com.osir.mcp.services.DomainService;
import com.osir.mcp.services.DomainSuggestionService;
import com.osir.mcp.services.TransferService;
import com.osir.mcp.services.HostService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain specialist agent handling domain registration, management, transfers,
 * suggestions, and host records via existing OSIR services.
 */
@ApplicationScoped
public class DomainSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(DomainSpecialistAgent.class);

    // Keyword groups for scoring — higher-weight keywords score more
    private static final Set<String> PRIMARY_KEYWORDS = Set.of(
            "domain", "register", "whois", "nameserver", "transfer", "tld"
    );
    private static final Set<String> SECONDARY_KEYWORDS = Set.of(
            "lock", "unlock", "renew", "privacy", "autorenew", "auto-renew",
            "suggest", "available", "check", "host", "glue", "ns"
    );

    // Valid skill IDs that can be passed explicitly via JSON-RPC params
    private static final Set<String> SKILL_IDS = Set.of(
            "check_availability", "register_domain", "get_domain_info", "list_domains",
            "suggest_domains", "transfer_domain",
            "renew_domain", "lock_domain", "unlock_domain",
            "enable_privacy", "disable_privacy", "enable_autorenew", "disable_autorenew"
    );

    @Inject DomainService domainService;
    @Inject DomainSuggestionService suggestionService;
    @Inject TransferService transferService;
    @Inject HostService hostService;

    @Override
    protected Set<String> getKeywords() { return PRIMARY_KEYWORDS; }

    @Override
    protected Set<String> getSkillIds() { return SKILL_IDS; }

    private AgentCard cachedCard;

    @PostConstruct
    void init() {
        cachedCard = buildAgentCard();
    }

    @Override
    public String getId() {
        return "domain-agent";
    }

    @Override
    public AgentCard getAgentCard() {
        return cachedCard;
    }

    @Override
    public double score(A2ATask task) {
        String text = getLatestUserMessage(task).toLowerCase();

        // If explicit skill is targeted at us, max score
        Map<String, Object> metadata = task.getMetadata();
        if (metadata != null) {
            String targetAgent = (String) metadata.get("agent");
            if (targetAgent != null) {
                return getId().equals(targetAgent) ? 1.0 : 0.0;
            }
            String skill = (String) metadata.get("skill");
            if (skill != null) {
                return SKILL_IDS.contains(skill) ? 1.0 : 0.0;
            }
        }

        // Score based on keyword density
        double score = 0.0;
        for (String kw : PRIMARY_KEYWORDS) {
            if (text.contains(kw)) score += 0.3;
        }
        for (String kw : SECONDARY_KEYWORDS) {
            if (text.contains(kw)) score += 0.15;
        }
        // Bonus if a domain name is present
        if (DOMAIN_PATTERN.matcher(text).find()) score += 0.2;

        return Math.min(score, 1.0);
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            if (skill != null) {
                return handleBySkill(task, skill);
            }
            return handleByIntent(task);
        } catch (Exception e) {
            LOG.errorf(e, "Domain agent error: %s", e.getMessage());
            return failWithException(task, e);
        }
    }

    // --- Explicit skill routing (preferred) ---

    private A2ATask handleBySkill(A2ATask task, String skill) {
        String text = getLatestUserMessage(task);
        return switch (skill) {
            case "check_availability" -> handleCheckAvailability(task, text);
            case "register_domain" -> handleRegisterDomain(task, text);
            case "get_domain_info" -> handleGetDomainInfo(task, text);
            case "list_domains" -> handleListDomains(task);
            case "renew_domain" -> handleRenewDomain(task, text);
            case "lock_domain" -> handleLockDomain(task, text);
            case "unlock_domain" -> handleUnlockDomain(task, text);
            case "suggest_domains" -> handleSuggestDomains(task, text);
            case "transfer_domain" -> handleTransferDomain(task, text);
            case "enable_privacy" -> handleDomainAction(task, text, true, true);
            case "disable_privacy" -> handleDomainAction(task, text, true, false);
            case "enable_autorenew" -> handleDomainAction(task, text, false, true);
            case "disable_autorenew" -> handleDomainAction(task, text, false, false);
            default -> {
                task.transitionTo(TaskState.FAILED);
                task.addMessage(new Message("agent", "Unknown skill: " + skill));
                yield task;
            }
        };
    }

    // --- Intent-based routing (fallback for unstructured messages) ---
    // Order matters: more specific intents checked first

    private A2ATask handleByIntent(A2ATask task) {
        String text = getLatestUserMessage(task);
        String lower = text.toLowerCase();

        // Most specific first
        if (lower.contains("auto") && lower.contains("renew")) {
            boolean enable = !lower.contains("disable");
            return handleDomainAction(task, text, false, enable);
        }
        if (lower.contains("privacy")) {
            boolean enable = !lower.contains("disable");
            return handleDomainAction(task, text, true, enable);
        }
        if (lower.contains("unlock")) {
            return handleUnlockDomain(task, text);
        }
        if (lower.contains("lock")) {
            return handleLockDomain(task, text);
        }
        if (lower.contains("transfer")) {
            return handleTransferDomain(task, text);
        }
        if (lower.contains("suggest")) {
            return handleSuggestDomains(task, text);
        }
        if (lower.contains("register")) {
            return handleRegisterDomain(task, text);
        }
        if (lower.contains("renew")) {
            return handleRenewDomain(task, text);
        }
        if (lower.contains("list") && lower.contains("domain")) {
            return handleListDomains(task);
        }
        if (lower.contains("info") || lower.contains("detail") || lower.contains("whois")) {
            return handleGetDomainInfo(task, text);
        }
        if (lower.contains("check") || lower.contains("available")) {
            return handleCheckAvailability(task, text);
        }

        // Last resort: if there's a domain in the text, check availability
        String domain = extractDomain(text);
        if (domain != null) {
            return handleCheckAvailability(task, domain);
        }

        task.transitionTo(TaskState.INPUT_REQUIRED);
        task.addMessage(new Message("agent",
                "I can help with: check availability, register, get info, list domains, " +
                "renew, lock/unlock, suggest, transfer, privacy, and auto-renew. " +
                "What would you like to do?"));
        return task;
    }

    // --- Operation handlers ---

    private A2ATask handleCheckAvailability(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "check");

        DomainAvailabilityResult result = domainService.checkAvailability(domain);
        task.addArtifact(Artifact.ofData("availability-result", toMap(result)));
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(TaskState.COMPLETED);
        return task;
    }

    private A2ATask handleRegisterDomain(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "register");

        // Registration requires registrant info — ask if not provided
        // For now, attempt with defaults; backend will return a clear error if registrant is missing
        DomainRegistrationResult result = domainService.registerDomain(domain, 1, null, null, true, true);
        if (!result.isSuccess() && result.getMessage() != null
                && result.getMessage().toLowerCase().contains("registrant")) {
            task.transitionTo(TaskState.INPUT_REQUIRED);
            task.addMessage(new Message("agent",
                    "Domain " + domain + " registration requires registrant contact information. " +
                    "Please provide: firstName, lastName, email, phone, street, city, state, postalCode, country."));
            return task;
        }

        task.addArtifact(Artifact.ofData("registration-result", toMap(result)));
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleGetDomainInfo(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "look up");

        DomainInfoResult result = domainService.getDomainInfo(domain);
        task.addArtifact(Artifact.ofData("domain-info", toMap(result)));
        task.addMessage(new Message("agent", result.isSuccess() ? "Domain info retrieved." : result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleListDomains(A2ATask task) {
        UserDomainsResult result = domainService.getUserDomains();
        task.addArtifact(Artifact.ofData("user-domains", toMap(result)));
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleRenewDomain(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "renew");

        DomainRenewalResult result = domainService.renewDomain(domain, 1);
        task.addArtifact(Artifact.ofData("renewal-result", toMap(result)));
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleLockDomain(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "lock");

        DomainActionResult result = domainService.lockDomain(domain);
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleUnlockDomain(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "unlock");

        DomainActionResult result = domainService.unlockDomain(domain);
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleSuggestDomains(A2ATask task, String text) {
        String keyword = text.replaceAll("(?i)(suggest|domain|names?|for|alternatives?|like|similar\\s+to)\\s*", "").trim();
        if (keyword.isEmpty()) keyword = extractDomain(text);
        if (keyword == null || keyword.isEmpty()) {
            task.transitionTo(TaskState.INPUT_REQUIRED);
            task.addMessage(new Message("agent", "Please provide a keyword or domain name for suggestions."));
            return task;
        }

        var result = suggestionService.suggestDomains(keyword, null, null, null, 10);
        task.addArtifact(Artifact.ofData("suggestions", toMap(result)));
        task.addMessage(new Message("agent", result.isSuccess() ? "Domain suggestions generated." : result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private A2ATask handleTransferDomain(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "transfer");

        Pattern authPattern = Pattern.compile(
                "(?:auth|epp|authorization)\\s*(?:code)?\\s*[:\\s]?\\s*([\\w-]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher authMatcher = authPattern.matcher(text);
        if (!authMatcher.find()) {
            task.transitionTo(TaskState.INPUT_REQUIRED);
            task.addMessage(new Message("agent",
                    "Please provide the authorization/EPP code for transferring " + domain + "."));
            return task;
        }

        String authCode = authMatcher.group(1);
        var result = transferService.initiateTransfer(domain, authCode);
        task.addArtifact(Artifact.ofData("transfer-result", toMap(result)));
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    /**
     * Handles privacy and auto-renew enable/disable.
     * @param isPrivacy true for privacy, false for auto-renew
     * @param enable true to enable, false to disable
     */
    private A2ATask handleDomainAction(A2ATask task, String text, boolean isPrivacy, boolean enable) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, (isPrivacy ? "privacy" : "auto-renew") + " settings");

        DomainActionResult result = isPrivacy
                ? domainService.updatePrivacyProtection(domain, enable)
                : domainService.updateAutoRenew(domain, enable);
        task.addMessage(new Message("agent", result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Domain Agent");
        card.setDescription("Manages domain registration, transfers, DNS host records, and domain suggestions for the OSIR registrar platform.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("check_availability", "Check Domain Availability",
                        "Check if a domain name is available for registration"),
                new Skill("register_domain", "Register Domain",
                        "Register a new domain name"),
                new Skill("get_domain_info", "Get Domain Info",
                        "Get detailed information about a registered domain"),
                new Skill("list_domains", "List Domains",
                        "List all domains owned by the authenticated user"),
                new Skill("renew_domain", "Renew Domain",
                        "Renew a domain for one year"),
                new Skill("lock_domain", "Lock Domain",
                        "Enable registrar lock to prevent unauthorized transfers"),
                new Skill("unlock_domain", "Unlock Domain",
                        "Remove registrar lock to allow transfers"),
                new Skill("suggest_domains", "Suggest Domains",
                        "Generate domain name suggestions based on keywords"),
                new Skill("transfer_domain", "Transfer Domain",
                        "Transfer a domain from another registrar"),
                new Skill("enable_privacy", "Enable Privacy",
                        "Enable WHOIS privacy protection"),
                new Skill("disable_privacy", "Disable Privacy",
                        "Disable WHOIS privacy protection"),
                new Skill("enable_autorenew", "Enable Auto-Renew",
                        "Enable automatic domain renewal"),
                new Skill("disable_autorenew", "Disable Auto-Renew",
                        "Disable automatic domain renewal")
        ));
        return card;
    }
}
