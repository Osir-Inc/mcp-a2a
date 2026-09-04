package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.models.*;
import com.osir.mcp.services.DomainService;
import com.osir.mcp.services.DomainSuggestionService;
import com.osir.mcp.services.TransferService;
import com.osir.a2a.security.ConfirmationGate;
import com.osir.mcp.security.DestructiveOpRateLimiter.Bucket;
import com.osir.mcp.services.CatalogService;
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
            "enable_privacy", "disable_privacy", "enable_autorenew", "disable_autorenew",
            // Read-only lookups and the free, non-billable writes (§ closed the A2A/MCP drift 2026-09-04)
            "validate_domain_name", "get_domain_extensions", "bulk_suggest_domains",
            "add_prefix", "add_suffix", "spin_domain_words", "check_keyword_availability",
            "update_nameservers",
            "get_transfer_quote", "get_transfer_status", "list_pending_transfers",
            "check_host_availability", "get_hosts_for_domain",
            ConfirmationGate.CONFIRM_SKILL
    );

    @Inject DomainService domainService;
    @Inject DomainSuggestionService suggestionService;
    @Inject TransferService transferService;
    @Inject HostService hostService;
    @Inject CatalogService catalogService;

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
            // Before any routing: a message carrying an actionId confirms what is already staged on
            // this task. Otherwise "yes, register it" re-enters the register branch and stages again.
            if (isConfirming(task)) {
                return runConfirmed(task);
            }
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
            case "validate_domain_name" -> handleValidateName(task, text);
            case "get_domain_extensions" -> handleDomainExtensions(task);
            case "bulk_suggest_domains" -> handleBulkSuggest(task);
            case "add_prefix" -> handleAffix(task, true);
            case "add_suffix" -> handleAffix(task, false);
            case "spin_domain_words" -> handleSpinWord(task);
            case "check_keyword_availability" -> handleKeywordAvailability(task);
            case "update_nameservers" -> handleUpdateNameservers(task, text);
            case "get_transfer_quote" -> handleTransferQuote(task, text);
            case "get_transfer_status" -> handleTransferStatus(task, text);
            case "list_pending_transfers" -> handleListPendingTransfers(task);
            case "check_host_availability" -> handleCheckHost(task);
            case "get_hosts_for_domain" -> handleHostsForDomain(task, text);
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

        int years = metaInt(task, "years") == null ? 1 : metaInt(task, "years");
        return stage(task, "register_domain", Map.of("domain", domain, "years", years),
                "Register " + domain + " for " + years + " year(s). This CHARGES THE ACCOUNT the "
                        + "registration fee (getDomainPricing has the exact amount) and starts an annual "
                        + "renewal. Domain registrations are not refundable.",
                Bucket.FINANCIAL);
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

        int years = metaInt(task, "years") == null ? 1 : metaInt(task, "years");
        return stage(task, "renew_domain", Map.of("domain", domain, "years", years),
                "Renew " + domain + " for " + years + " year(s). This CHARGES THE ACCOUNT the renewal "
                        + "fee and is not refundable.",
                Bucket.FINANCIAL);
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
        // The auth code is frozen with the rest: it is already in this task's message history either
        // way, so staging adds no exposure it did not already have.
        return stage(task, "transfer_domain", Map.of("domain", domain, "authCode", authCode),
                "Transfer " + domain + " to OSIR. This CHARGES THE ACCOUNT a transfer fee (which adds a "
                        + "year to the registration) and starts a registry process that takes up to 5 days "
                        + "and cannot be cancelled once the losing registrar approves it.",
                Bucket.FINANCIAL);
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

    /** Run what was staged, from the parameters frozen at stage time. */
    private A2ATask runConfirmed(A2ATask task) {
        var claim = confirmationGate.claim(task);
        if (!claim.ok()) {
            return failWithError(task, claim.error());
        }
        Map<String, Object> p = claim.action().params();
        String domain = String.valueOf(p.get("domain"));
        int years = p.get("years") instanceof Number n ? n.intValue() : 1;
        switch (claim.action().skill()) {
            case "register_domain" -> {
                var result = domainService.registerDomain(domain, years, null, null, true, true);
                return completeWithResult(task, "registration-result", result, result.isSuccess(),
                        result.getMessage());
            }
            case "renew_domain" -> {
                var result = domainService.renewDomain(domain, years);
                return completeWithResult(task, "renewal-result", result, result.isSuccess(),
                        result.getMessage());
            }
            case "transfer_domain" -> {
                var result = transferService.initiateTransfer(domain, String.valueOf(p.get("authCode")));
                return completeWithResult(task, "transfer-result", result, result.isSuccess(),
                        result.getMessage());
            }
            default -> {
                return failWithError(task, "Cannot run '" + claim.action().skill() + "': unknown staged action.");
            }
        }
    }

    // ---- Lookups and free operations that the MCP has always had (drift closed 2026-09-04) ----

    private A2ATask handleValidateName(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "validate");

        var result = domainService.validateDomainName(domain);
        return completeWithResult(task, "domain-validation", result, result.isValid(),
                result.isValid() ? domain + " is a valid domain name." : result.getMessage());
    }

    private A2ATask handleDomainExtensions(A2ATask task) {
        var result = catalogService.getDomainExtensions();
        return completeWithResult(task, "domain-extensions", result, result.isSuccess(),
                result.isSuccess() ? "TLD catalog retrieved." : result.getMessage());
    }

    private A2ATask handleBulkSuggest(A2ATask task) {
        List<String> keywords = csv(meta(task, "keywords"));
        if (keywords == null) {
            return askForInput(task, "To suggest names in bulk, please provide in metadata: keywords "
                    + "(comma-separated). Optional: tlds (comma-separated), lang, maxResults.");
        }
        var result = suggestionService.bulkSuggestions(keywords, csv(meta(task, "tlds")),
                meta(task, "lang"), metaInt(task, "maxResults"));
        return completeWithResult(task, "domain-suggestions", result, result.isSuccess(),
                result.isSuccess() ? "Suggestions generated." : result.getMessage());
    }

    /** add_prefix and add_suffix differ only in which end the word goes on. */
    private A2ATask handleAffix(A2ATask task, boolean prefix) {
        String name = meta(task, "name");
        if (name == null) {
            return askForInput(task, "To build name variants, please provide in metadata: name. "
                    + "Optional: vocabulary, tlds (comma-separated), lang, maxResults.");
        }
        String vocabulary = meta(task, "vocabulary");
        String tlds = meta(task, "tlds");
        String lang = meta(task, "lang");
        Integer max = metaInt(task, "maxResults");
        var result = prefix
                ? suggestionService.addPrefix(name, vocabulary, tlds, lang, max)
                : suggestionService.addSuffix(name, vocabulary, tlds, lang, max);
        return completeWithResult(task, "domain-suggestions", result, result.isSuccess(),
                result.isSuccess() ? "Suggestions generated." : result.getMessage());
    }

    private A2ATask handleSpinWord(A2ATask task) {
        String name = meta(task, "name");
        if (name == null) {
            return askForInput(task, "To spin variants of a name, please provide in metadata: name. "
                    + "Optional: position, similarity (0-1), tlds (comma-separated), lang, maxResults.");
        }
        var result = suggestionService.spinWord(name, metaInt(task, "position"),
                metaDouble(task, "similarity"), meta(task, "tlds"), meta(task, "lang"),
                metaInt(task, "maxResults"));
        return completeWithResult(task, "domain-suggestions", result, result.isSuccess(),
                result.isSuccess() ? "Suggestions generated." : result.getMessage());
    }

    private A2ATask handleKeywordAvailability(A2ATask task) {
        String keyword = meta(task, "keyword");
        if (keyword == null) {
            return askForInput(task, "To check a keyword, please provide in metadata: keyword. "
                    + "Optional: registries, tlds, summary (true for counts only).");
        }
        String registries = meta(task, "registries");
        String tlds = meta(task, "tlds");
        boolean summary = Boolean.parseBoolean(meta(task, "summary"));
        var result = summary
                ? suggestionService.checkKeywordAvailabilitySummary(keyword, registries, tlds)
                : suggestionService.checkKeywordAvailability(keyword, registries, tlds);
        // The backend returns the payload straight through; there is no success flag on it.
        return completeWithResult(task, "keyword-availability", result, result != null,
                result != null ? "Keyword availability retrieved." : "Keyword availability is unavailable right now.");
    }

    private A2ATask handleUpdateNameservers(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        List<String> nameservers = csv(meta(task, "nameservers"));
        if (domain == null || nameservers == null) {
            return askForInput(task, "To change nameservers, please provide in metadata: domain and "
                    + "nameservers (comma-separated, e.g. ns1.osir.com,ns3.osir.com).");
        }
        var result = domainService.updateNameservers(domain, nameservers);
        return completeWithResult(task, "nameservers", result, result.isSuccess(),
                result.isSuccess() ? "Nameservers updated for " + domain + "." : result.getMessage());
    }

    private A2ATask handleTransferQuote(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "quote a transfer for");

        var result = transferService.getQuote(domain);
        return completeWithResult(task, "transfer-quote", result, result.isSuccess(),
                result.isSuccess() ? "Transfer quote retrieved." : result.getMessage());
    }

    private A2ATask handleTransferStatus(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "check the transfer status of");

        var result = transferService.getStatus(domain);
        return completeWithResult(task, "transfer-status", result, result.isSuccess(),
                result.isSuccess() ? "Transfer status retrieved." : result.getMessage());
    }

    private A2ATask handleListPendingTransfers(A2ATask task) {
        var result = transferService.listPending();
        return completeWithResult(task, "pending-transfers", result, result.isSuccess(),
                result.isSuccess() ? "Pending transfers retrieved." : result.getMessage());
    }

    private A2ATask handleCheckHost(A2ATask task) {
        String hostname = meta(task, "hostname");
        if (hostname == null) {
            return askForInput(task, "Please provide the host name to check in metadata: hostname "
                    + "(e.g. ns1.cedarloop.com).");
        }
        var result = hostService.checkAvailability(hostname);
        return completeWithResult(task, "host-check", result, result.isSuccess(),
                result.isSuccess() ? "Host availability checked." : result.getMessage());
    }

    private A2ATask handleHostsForDomain(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "list host records for");

        var result = hostService.getHostsForDomain(domain);
        return completeWithResult(task, "hosts", result, result.isSuccess(),
                result.isSuccess() ? "Host records retrieved." : result.getMessage());
    }

    /** Comma-separated metadata value to a list; null (not an empty list) when absent or blank. */
    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        List<String> parts = java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(p -> !p.isEmpty()).toList();
        return parts.isEmpty() ? null : parts;
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
                        "Check if a domain name is available for registration",
                        List.of("domains", "availability", "check"),
                        List.of("Is coolstartup.io available?", "Can I get brightharbor.com?")),
                new Skill("register_domain", "Register Domain",
                        "Register a new domain name",
                        List.of("domains", "register", "purchase"),
                        List.of("Register mapleworks.dev for me", "I want to buy northpine.com")),
                new Skill("get_domain_info", "Get Domain Info",
                        "Get detailed information about a registered domain",
                        List.of("domains", "info", "whois"),
                        List.of("Show me the details for brahaj.al", "When does silvergate.net expire?")),
                new Skill("list_domains", "List Domains",
                        "List all domains owned by the authenticated user",
                        List.of("domains", "list", "portfolio"),
                        List.of("List all my domains", "Which domains do I own?")),
                new Skill("renew_domain", "Renew Domain",
                        "Renew a domain for one year",
                        List.of("domains", "renew", "expiry"),
                        List.of("Renew brahaj.al for another year", "Extend the registration on cedarloop.com")),
                new Skill("lock_domain", "Lock Domain",
                        "Enable registrar lock to prevent unauthorized transfers",
                        List.of("domains", "lock", "security"),
                        List.of("Lock ironvale.com so nobody can transfer it", "Enable the transfer lock on my domain")),
                new Skill("unlock_domain", "Unlock Domain",
                        "Remove registrar lock to allow transfers",
                        List.of("domains", "unlock", "transfer"),
                        List.of("Unlock ironvale.com, I am moving it to another registrar")),
                new Skill("suggest_domains", "Suggest Domains",
                        "Generate domain name suggestions based on keywords",
                        List.of("domains", "suggestions", "brainstorm"),
                        List.of("Suggest some domain names for my bakery", "Give me alternatives to coolstartup.io")),
                new Skill("transfer_domain", "Transfer Domain",
                        "Transfer a domain from another registrar",
                        List.of("domains", "transfer", "epp"),
                        List.of("Transfer quietriver.org to OSIR, the auth code is QX7-2291",
                                "Move my domain from GoDaddy to you")),
                new Skill("enable_privacy", "Enable Privacy",
                        "Enable WHOIS privacy protection",
                        List.of("domains", "privacy", "whois"),
                        List.of("Hide my WHOIS info for brahaj.al", "Turn on privacy protection for cedarloop.com")),
                new Skill("disable_privacy", "Disable Privacy",
                        "Disable WHOIS privacy protection",
                        List.of("domains", "privacy", "whois"),
                        List.of("Turn off WHOIS privacy for cedarloop.com")),
                new Skill("enable_autorenew", "Enable Auto-Renew",
                        "Enable automatic domain renewal",
                        List.of("domains", "autorenew", "renewal"),
                        List.of("Enable auto-renew on brahaj.al", "Make sure northpine.com renews automatically")),
                new Skill("disable_autorenew", "Disable Auto-Renew",
                        "Disable automatic domain renewal",
                        List.of("domains", "autorenew", "renewal"),
                        List.of("Turn off auto-renew for silvergate.net, I am letting it expire")),
                new Skill(ConfirmationGate.CONFIRM_SKILL, "Confirm A Staged Action",
                        "Run an action this agent staged: send the actionId it returned, on the same task",
                        List.of("confirmation", "safety"),
                        List.of("Confirm action a2a_1f4c... on this task",
                                "Yes, go ahead with the registration you summarised")),
                new Skill("validate_domain_name", "Validate Domain Name",
                        "Check whether a name is syntactically registrable, before spending a lookup on it",
                        List.of("domains", "validate", "syntax"),
                        List.of("Is 'my--shop.com' a valid domain name?",
                                "Can a domain start with a hyphen?")),
                new Skill("get_domain_extensions", "Get Domain Extensions",
                        "The TLDs OSIR sells, with registration and renewal prices",
                        List.of("domains", "tld", "extensions", "catalog"),
                        List.of("Which TLDs do you support?", "Do you sell .dev domains?")),
                new Skill("bulk_suggest_domains", "Bulk Domain Suggestions",
                        "Name ideas for several keywords at once across chosen TLDs",
                        List.of("domains", "suggestions", "bulk", "naming"),
                        List.of("Suggest names for 'cedar', 'loop' and 'harbor' on .com and .io",
                                "Give me domain ideas for a coffee roastery")),
                new Skill("add_prefix", "Add Prefix To Domain",
                        "Name variants built by putting a word in front of the keyword",
                        List.of("domains", "suggestions", "naming"),
                        List.of("Show me prefixed variants of 'harbor'", "Names like get-harbor or try-harbor")),
                new Skill("add_suffix", "Add Suffix To Domain",
                        "Name variants built by appending a word to the keyword",
                        List.of("domains", "suggestions", "naming"),
                        List.of("Suffix variants of 'cedar'", "Names like cedarhq or cedarlabs")),
                new Skill("spin_domain_words", "Spin Domain Words",
                        "Near-miss variants of a name, tuned by similarity",
                        List.of("domains", "suggestions", "naming"),
                        List.of("Spin variants of 'brightharbor'", "Names close to 'cedarloop' but available")),
                new Skill("check_keyword_availability", "Check Keyword Availability",
                        "Which TLDs a keyword is still free on (pass summary=true for counts only)",
                        List.of("domains", "availability", "keyword"),
                        List.of("Where is 'cedarloop' still available?",
                                "Is the word 'harbor' free on any good TLD?")),
                new Skill("update_nameservers", "Update Nameservers",
                        "Point a domain at a different set of nameservers",
                        List.of("domains", "nameservers", "dns"),
                        List.of("Point cedarloop.com at ns1.osir.com and ns3.osir.com",
                                "Move brahaj.al to Cloudflare's nameservers")),
                new Skill("get_transfer_quote", "Get Transfer Quote",
                        "What transferring a domain in would cost, and whether it is eligible",
                        List.of("domains", "transfer", "pricing"),
                        List.of("What would it cost to transfer cedarloop.com to you?",
                                "Can I move silvergate.net here yet?")),
                new Skill("get_transfer_status", "Get Transfer Status",
                        "Where an in-flight transfer has got to",
                        List.of("domains", "transfer", "status"),
                        List.of("How is the transfer of cedarloop.com going?",
                                "Has my domain moved yet?")),
                new Skill("list_pending_transfers", "List Pending Transfers",
                        "Every transfer currently in flight on the account",
                        List.of("domains", "transfer", "list"),
                        List.of("Which of my transfers are still pending?", "Show me transfers in progress")),
                new Skill("check_host_availability", "Check Host Availability",
                        "Whether a glue/host record name is free to create",
                        List.of("domains", "host", "glue", "nameserver"),
                        List.of("Is ns1.cedarloop.com free as a host record?")),
                new Skill("get_hosts_for_domain", "Get Hosts For Domain",
                        "The glue/host records registered under a domain",
                        List.of("domains", "host", "glue", "nameserver"),
                        List.of("Which glue records exist on cedarloop.com?",
                                "Show me the host records for brahaj.al"))
        ));
        return card;
    }
}
