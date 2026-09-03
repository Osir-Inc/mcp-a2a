package com.osir.mcp;

import com.osir.mcp.models.*;
import com.osir.mcp.models.auth.DeviceLoginResult;
import com.osir.mcp.models.auth.DeviceLoginStatusResult;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.contact.RegistrantInfo;
import com.osir.mcp.models.suggestion.BulkDomainSuggestionsResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.DomainService;
import com.osir.mcp.services.DomainSuggestionService;
import com.osir.mcp.services.McpAuthHelper;
import com.osir.mcp.services.SessionAwareAuthService;
import com.osir.mcp.services.ToolErrors;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import com.osir.mcp.models.McpError;


@McpAudited
@ApplicationScoped
public class DomainRegistrarMCPServer {

    private static final Logger AUDIT = Logger.getLogger("com.osir.mcp.audit");

    @Inject
    DomainService domainService;

    @Inject
    SessionAwareAuthService sessionAuthService;

    @Inject
    McpAuthHelper mcpAuthHelper;

    @Inject
    DomainSuggestionService domainSuggestionService;

    @Inject
    PendingActionStore pendingActionStore;

    // ── Authentication ────────────────────────────────────────────────────────

    @Tool(name = "loginWithDevice", description = "loginWithDevice: Start a device authorization login (RFC 8628). Returns a verificationUri and userCode. Open the URI in your browser, enter the code, and sign in with your OSIR credentials. Then call checkDeviceLoginStatus with the returned deviceCode to complete login. No parameters required.",
            annotations = @Tool.Annotations(
                    title = "Start device login",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DeviceLoginResult loginWithDevice(McpConnection connection) {
        Log.infof("Starting device authorization login flow for connection %s", connection.id());
        try {
            return sessionAuthService.startDeviceLogin(connection.id());
        } catch (Exception e) {
            return new DeviceLoginResult(false, "Device login failed: " + e.getMessage());
        }
    }

    @Tool(description = "checkDeviceLoginStatus: Poll for device login completion. Call this after loginWithDevice() once you have opened the verification URL and signed in. Required: deviceCode (the device_code returned by loginWithDevice). On success returns a sessionKey; pass it as the sessionKey argument on every subsequent authenticated tool call.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Check device login status",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DeviceLoginStatusResult checkDeviceLoginStatus(
            @ToolArg(description = "The device_code value returned by loginWithDevice.") String deviceCode,
            McpConnection connection) {
        Log.infof("Checking device login status for connection %s", connection.id());
        try {
            return sessionAuthService.checkDeviceLoginStatus(connection.id(), deviceCode);
        } catch (Exception e) {
            return new DeviceLoginStatusResult(false, "Status check failed: " + e.getMessage(), "error");
        }
    }

    @Tool(description = "getAuthStatus: Check whether the current session is authenticated. Returns authenticated status and token expiry. Optional: sessionKey (from checkDeviceLoginStatus).",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get authentication status",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AuthStatusResult getAuthStatus(
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        // Mirror resolveToken's precedence exactly: an EXPIRED bearer must fall through to the
        // sessionKey and then the per-connection session (which every other tool would use), not
        // short-circuit as authenticated:false. Only when nothing authenticates do we report the
        // most specific negative status.
        AuthStatusResult bearerStatus = mcpAuthHelper.bearerAuthStatus();
        if (bearerStatus != null && bearerStatus.isAuthenticated()) return bearerStatus;
        AuthStatusResult sessionStatus = null;
        if (sessionKey != null && !sessionKey.isBlank()) {
            sessionStatus = sessionAuthService.getAuthStatus(sessionKey);
            if (sessionStatus.isAuthenticated()) return sessionStatus;
        }
        AuthStatusResult connStatus = sessionAuthService.getAuthStatus(connection.id());
        if (connStatus.isAuthenticated()) return connStatus;
        if (sessionStatus != null) return sessionStatus;
        if (bearerStatus != null) return bearerStatus;
        return connStatus;
    }

    @Tool(description = "logout: Log out: revokes the session's tokens at the identity provider immediately. Optional: sessionKey (from checkDeviceLoginStatus); pass it to end that conversation session.",
            annotations = @Tool.Annotations(
                    title = "Log out",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public AuthResult logout(
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        if (sessionKey != null && !sessionKey.isBlank()) {
            return sessionAuthService.logout(sessionKey);
        }
        String bearer = mcpAuthHelper.bearerToken();
        if (bearer != null) {
            return sessionAuthService.revokeBearer(bearer);
        }
        return sessionAuthService.logout(connection.id());
    }

    // ── Domain Availability ───────────────────────────────────────────────────

    // Domain Availability Tools
    @Tool(description = "checkDomainAvailability: Check if a domain name is available for registration, with price. No authentication required; anonymous callers get list pricing, authenticated callers get their account pricing. Required: domain (e.g., 'example.com')",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Check domain availability",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainAvailabilityResult checkDomainAvailability(
            @ToolArg(description = "Fully qualified domain name to check, like \"example.com\", without scheme.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        mcpAuthHelper.setupAuth(connection, sessionKey);
        try {
            return domainService.checkAvailability(domain);
        } catch (Exception e) {
            // Honest error, never a fabricated available:false (audit F1).
            throw ToolErrors.toolError("Availability check for '" + domain + "'", e);
        }
    }

//    @Tool(description = "Check availability for multiple domain names at once")
//    public BulkAvailabilityResult bulkCheckDomains(List<String> domains) {
//        try {
//            return domainService.bulkCheckAvailability(domains);
//        } catch (Exception e) {
//            return new BulkAvailabilityResult(false, "Bulk check failed: " + e.getMessage());
//        }
//    }

    // Domain Registration Tools
    @RequiresAuth
    @Tool(description = "registerDomain: Stage registration of a new domain name. Deducts from account balance. The DNS zone is initialised automatically after registration (asynchronously; if createDnsRecord right after registration reports a missing zone, retry after a few seconds). Pass initializeDnsZone:false to opt out. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Register a domain",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult registerDomain(
            @ToolArg(description = "Fully qualified domain name to register, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "Registration period in years, 1-10.") int years,
            @ToolArg(description = "ICANN registrant contact of the domain owner: firstName, lastName, email, phone (+CC.number), and address (street, city, postalCode, country as 2-letter ISO code).") RegistrantInfo registrantInfo,
            @ToolArg(description = "List of nameserver hostnames, e.g. [\"ns1.example.com\", \"ns2.example.com\"].") List<String> nameservers,
            @ToolArg(required = false, description = "Enable WHOIS privacy protection; defaults to true.") Boolean privacyProtection,
            @ToolArg(required = false, description = "Enable automatic renewal; defaults to true.") Boolean autoRenew,
            @ToolArg(required = false, description = "Initialise the DNS zone after registration; defaults to true.") Boolean initializeDnsZone,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection
    ) {
        boolean privacy = privacyProtection != null ? privacyProtection : true;
        boolean renew = autoRenew != null ? autoRenew : true;
        return pendingActionStore.stage(
                "registerDomain",
                "Register domain '" + domain + "' for " + years + " year(s), deducts registration fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.registerDomain(domain, years, registrantInfo, nameservers, privacy, renew, initializeDnsZone)
        );
    }

    // Domain Transfer Tools
    @RequiresAuth
    @Tool(description = "transferDomain: Stage transfer of a domain from another registrar to OSIR. Deducts from account balance. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Transfer a domain",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult transferDomain(
            @ToolArg(description = "Fully qualified domain name to transfer, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "EPP authorization code obtained from the current registrar.") String authCode,
            @ToolArg(description = "ICANN registrant contact of the domain owner: firstName, lastName, email, phone (+CC.number), and address (street, city, postalCode, country as 2-letter ISO code).") RegistrantInfo registrantInfo,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "transferDomain",
                "Transfer domain '" + domain + "' to OSIR, deducts transfer fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.transferDomain(domain, authCode, registrantInfo)
        );
    }

    // Domain Management Tools
    @RequiresAuth
    @Tool(description = "updateNameservers: Update nameservers for a domain. Replaces the current nameserver set with the given list.",
            annotations = @Tool.Annotations(
                    title = "Update nameservers",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public NameserverUpdateResult updateNameservers(
            @ToolArg(description = "Fully qualified domain name, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "List of nameserver hostnames, e.g. [\"ns1.example.com\", \"ns2.example.com\"].") List<String> nameservers,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.updateNameservers(domain, nameservers);
        } catch (Exception e) {
            return new NameserverUpdateResult(domain, false, "Nameserver update failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "getDomainInfo: Get registry (EPP) state plus account settings for one domain: status, nameservers, lock state, auto-renew, privacy, creation/expiry dates, premium/expired/redemption info. Dates are null while a registration is still pending at the registry; autoRenew is omitted for transferredOut domains.",
            annotations = @Tool.Annotations(
                    title = "Get domain details",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainInfoResult getDomainInfo(
            @ToolArg(description = "Fully qualified domain name, like \"example.com\", without scheme.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.getDomainInfo(domain);
        } catch (Exception e) {
            return new DomainInfoResult(domain, false, "Failed to get domain info: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "listUserDomains: List all domains owned by the authenticated user. No parameters required. Must be authenticated first.",
            annotations = @Tool.Annotations(
                    title = "List my domains",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public UserDomainsResult listUserDomains(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.getUserDomains();
        } catch (Exception e) {
            return new UserDomainsResult(false, "Failed to list domains: " + e.getMessage());
        }
    }

    // Domain Renewal
    @RequiresAuth
    @Tool(description = "renewDomain: Stage renewal of a domain for a specified number of years. Deducts from account balance. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Renew a domain",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult renewDomain(
            @ToolArg(description = "Fully qualified domain name to renew, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "Renewal period in years, 1-10.") int years,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "renewDomain",
                "Renew domain '" + domain + "' for " + years + " year(s), deducts renewal fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.renewDomain(domain, years)
        );
    }

    // Domain Lock/Unlock
    @RequiresAuth
    @Tool(description = "lockDomain: Enable registrar lock on a domain to prevent unauthorized transfers.",
            annotations = @Tool.Annotations(
                    title = "Lock a domain",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainActionResult lockDomain(
            @ToolArg(description = "Fully qualified domain name to lock, like \"example.com\", without scheme.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.lockDomain(domain);
        } catch (Exception e) {
            return new DomainActionResult(false, "Lock failed: " + e.getMessage(), domain, null);
        }
    }

    @RequiresAuth
    @Tool(description = "unlockDomain: Stage removal of registrar lock from a domain to allow transfers. DESTRUCTIVE: reduces domain security. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Unlock a domain",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult unlockDomain(
            @ToolArg(description = "Fully qualified domain name to unlock, like \"example.com\", without scheme.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "unlockDomain",
                "Remove registrar lock from domain '" + domain + "', reduces security, enables transfers",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> domainService.unlockDomain(domain)
        );
    }

    // Domain Settings
    @RequiresAuth
    @Tool(description = "updateDomainAutoRenew: Enable or disable auto-renewal for a domain.",
            annotations = @Tool.Annotations(
                    title = "Set domain auto-renew",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DomainActionResult updateDomainAutoRenew(
            @ToolArg(description = "Fully qualified domain name, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "true to enable automatic renewal, false to disable it.") boolean enabled,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.updateAutoRenew(domain, enabled);
        } catch (Exception e) {
            return new DomainActionResult(false, "Auto-renew update failed: " + e.getMessage(), domain, null);
        }
    }

    @RequiresAuth
    @Tool(description = "updateDomainPrivacy: Enable or disable WHOIS privacy protection for a domain.",
            annotations = @Tool.Annotations(
                    title = "Set WHOIS privacy",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DomainActionResult updateDomainPrivacy(
            @ToolArg(description = "Fully qualified domain name, like \"example.com\", without scheme.") String domain,
            @ToolArg(description = "true to enable WHOIS privacy protection, false to disable it.") boolean enabled,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.updatePrivacyProtection(domain, enabled);
        } catch (Exception e) {
            return new DomainActionResult(false, "Privacy update failed: " + e.getMessage(), domain, null);
        }
    }

    // Utility Tools
    @Tool(description = "validateDomainName: Validate if a domain name format is correct. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "Validate domain name",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ValidationResult validateDomainName(
            @ToolArg(description = "Fully qualified domain name to validate, like \"example.com\", without scheme.") String domain,
            McpConnection connection) {
        return domainService.validateDomainName(domain);
    }

    @RequiresAuth
    @Tool(description = "suggestAlternatives: Suggest alternative domain names if the requested one is unavailable. Legacy; prefer generateDomainSuggestions.",
            annotations = @Tool.Annotations(
                    title = "Suggest alternative domains",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainSuggestionsResult suggestAlternatives(
            @ToolArg(description = "Fully qualified domain name to find alternatives for, like \"example.com\", without scheme.") String domain,
            @ToolArg(required = false, description = "Maximum number of suggestions to return; default 10.") Integer limit,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return domainService.suggestAlternatives(domain, limit != null ? limit : 10);
        } catch (Exception e) {
            return new DomainSuggestionsResult(false, "Failed to generate suggestions: " + e.getMessage());
        }
    }

    // Domain Suggestion Tools
    @Tool(description = "generateDomainSuggestions: Generate domain name suggestions based on keywords. This is the preferred suggestion tool for a single keyword; use it over suggestAlternatives. Returns suggested names with availability.",
            annotations = @Tool.Annotations(
                    title = "Generate domain suggestions",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult generateDomainSuggestions(
            @ToolArg(description = "Keyword or base name to build suggestions from, e.g. \"mycompany\".") String name,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            @ToolArg(required = false, description = "Language code; default \"eng\".") String lang,
            @ToolArg(required = false, description = "Allow digits in generated suggestions (true/false).") Boolean useNumbers,
            @ToolArg(required = false, description = "Maximum suggestions to return; default 20.") Integer maxResults,
            McpConnection connection) {
        try {
            return domainSuggestionService.suggestDomains(name, tlds, lang, useNumbers, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate domain suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "spinDomainWords: Generate domain suggestions by spinning/replacing words with similar alternatives.",
            annotations = @Tool.Annotations(
                    title = "Spin domain words",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult spinDomainWords(
            @ToolArg(description = "Comma-separated words to spin, e.g. \"pizza,restaurant\".") String name,
            @ToolArg(required = false, description = "0-based index of the word to replace.") Integer position,
            @ToolArg(required = false, description = "Similarity threshold for replacements, 0.0-1.0.") Double similarity,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            @ToolArg(required = false, description = "Language code; default \"eng\".") String lang,
            @ToolArg(required = false, description = "Maximum suggestions to return; default 20.") Integer maxResults,
            McpConnection connection) {
        try {
            return domainSuggestionService.spinWord(name, position, similarity, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate word spin suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "addPrefixToDomain: Generate domain suggestions by adding prefixes to a base name.",
            annotations = @Tool.Annotations(
                    title = "Add domain prefixes",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult addPrefixToDomain(
            @ToolArg(description = "Base name to prefix, e.g. \"mycompany\".") String name,
            @ToolArg(required = false, description = "Prefix vocabulary: \"@prefixes\" or a custom comma-separated list.") String vocabulary,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            @ToolArg(required = false, description = "Language code; default \"eng\".") String lang,
            @ToolArg(required = false, description = "Maximum suggestions to return; default 20.") Integer maxResults,
            McpConnection connection) {
        try {
            return domainSuggestionService.addPrefix(name, vocabulary, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate prefix suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "addSuffixToDomain: Generate domain suggestions by adding suffixes to a base name.",
            annotations = @Tool.Annotations(
                    title = "Add domain suffixes",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult addSuffixToDomain(
            @ToolArg(description = "Base name to suffix, e.g. \"mycompany\".") String name,
            @ToolArg(required = false, description = "Suffix vocabulary: \"@suffixes\" or a custom comma-separated list.") String vocabulary,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            @ToolArg(required = false, description = "Language code; default \"eng\".") String lang,
            @ToolArg(required = false, description = "Maximum suggestions to return; default 20.") Integer maxResults,
            McpConnection connection) {
        try {
            return domainSuggestionService.addSuffix(name, vocabulary, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate suffix suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "bulkDomainSuggestions: Generate domain suggestions for 1-10 keywords across 1-6 TLDs (hard cap), grouped by originating keyword. Typical flow: call listCategorizedTlds first to pick 3-6 TLDs, then this tool. Per-suggestion availability may be \"available\", \"taken\", or \"unknown\"; confirm \"unknown\" or premium-TLD names with checkDomainAvailability before recommending.",
            annotations = @Tool.Annotations(
                    title = "Bulk domain suggestions",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public BulkDomainSuggestionsResult bulkDomainSuggestions(
            @ToolArg(description = "1-10 keywords describing the project.") List<String> keywords,
            @ToolArg(description = "1-6 TLDs without leading dots (use \"tech\", not \".tech\"), chosen from listCategorizedTlds.") List<String> tlds,
            @ToolArg(required = false, description = "Language code; default \"eng\".") String lang,
            @ToolArg(required = false, description = "Maximum suggestions per keyword; default 20.") Integer maxResults,
            McpConnection connection) {
        if (tlds != null && tlds.size() > 6) {
            return new BulkDomainSuggestionsResult(false,
                    "tlds must contain between 1 and 6 entries (received " + tlds.size() +
                    "). Use listCategorizedTlds to pick a focused TLD set first.");
        }
        AUDIT.infof("tool=bulkDomainSuggestions conn=%s keywords=%d tlds=%d",
                connection.id(),
                keywords != null ? keywords.size() : 0,
                tlds != null ? tlds.size() : 0);
        try {
            return domainSuggestionService.bulkSuggestions(keywords, tlds, lang, maxResults);
        } catch (Exception e) {
            return new BulkDomainSuggestionsResult(false, "Failed to generate bulk suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "checkKeywordAvailability: Check keyword availability across all supported TLDs and registries with detailed per-domain results. Use checkKeywordAvailabilitySummary instead when you only need counts; it is faster.",
            annotations = @Tool.Annotations(
                    title = "Check keyword availability",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public Object checkKeywordAvailability(
            @ToolArg(description = "Keyword to check, without a TLD, e.g. \"example\".") String keyword,
            @ToolArg(required = false, description = "Comma-separated registry filter, e.g. \"verisign,pir,id,centralnic\".") String registries,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            McpConnection connection) {
        try {
            return domainSuggestionService.checkKeywordAvailability(keyword, registries, tlds);
        } catch (Exception e) {
            Log.errorf(e, "Error checking keyword availability for: %s", keyword);
            return new McpError("KEYWORD_CHECK_FAILED", "Failed to check keyword availability: " + e.getMessage());
        }
    }

    @Tool(description = "checkKeywordAvailabilitySummary: Check keyword availability across TLDs and registries. Summary statistics only (no per-domain results), faster than checkKeywordAvailability.",
            annotations = @Tool.Annotations(
                    title = "Keyword availability summary",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public Object checkKeywordAvailabilitySummary(
            @ToolArg(description = "Keyword to check, without a TLD, e.g. \"example\".") String keyword,
            @ToolArg(required = false, description = "Comma-separated registry filter, e.g. \"verisign,pir\".") String registries,
            @ToolArg(required = false, description = "Comma-separated TLDs without leading dots, e.g. \"com,net\".") String tlds,
            McpConnection connection) {
        try {
            return domainSuggestionService.checkKeywordAvailabilitySummary(keyword, registries, tlds);
        } catch (Exception e) {
            Log.errorf(e, "Error checking keyword availability summary for: %s", keyword);
            return new McpError("KEYWORD_CHECK_FAILED", "Failed to check keyword availability summary: " + e.getMessage());
        }
    }

    // Prompt for domain registration guidance
    @Prompt(name = "domain_registration_guide")
    public PromptMessage domainRegistrationGuide(@PromptArg(name = "domain_type") String domainType) {
        String content = switch (domainType.toLowerCase()) {
            case "business" -> """
                Domain Registration Guide for Business:
                1. Choose a domain that matches your business name
                2. Consider .com, .net, or industry-specific TLDs
                3. Keep it short, memorable, and easy to spell
                4. Avoid hyphens and numbers if possible
                5. Check trademark issues before registering
                6. Enable privacy protection to protect your information
                7. Set up auto-renewal to prevent accidental expiration
                """;
            case "personal" -> """
                Domain Registration Guide for Personal Use:
                1. Consider using your name (firstname-lastname.com)
                2. Think about your personal brand or interests
                3. .com is still the most trusted extension
                4. Consider creative TLDs like .me, .io, .dev for tech-focused sites
                5. Keep it simple and professional
                6. Enable privacy protection
                """;
            case "ecommerce" -> """
                Domain Registration Guide for E-commerce:
                1. Include keywords related to your products/services
                2. Keep it brandable and memorable
                3. .com is essential for trust and credibility
                4. Avoid trademark conflicts
                5. Consider registering multiple extensions (.net, .org)
                6. Make it easy to type and remember
                7. Test how it sounds when spoken aloud
                """;
            default -> """
                General Domain Registration Guide:
                1. Choose a memorable and relevant domain name
                2. Verify availability and pricing
                3. Provide accurate registrant information
                4. Configure nameservers (or use registrar's defaults)
                5. Enable privacy protection if desired
                6. Set up auto-renewal to prevent expiration
                7. Keep your contact information updated
                """;
        };

        return PromptMessage.withUserRole(new TextContent(content));
    }

    @Prompt(name = "domain_transfer_checklist")
    public PromptMessage domainTransferChecklist() {
        String content = """
            Domain Transfer Checklist:
            
            Before Transfer:
            ☐ Unlock the domain at current registrar
            ☐ Obtain authorization/EPP code
            ☐ Verify domain is eligible for transfer (60+ days old)
            ☐ Ensure domain doesn't expire soon (30+ days remaining)
            ☐ Update contact information if needed
            
            During Transfer:
            ☐ Initiate transfer with new registrar
            ☐ Provide authorization code
            ☐ Confirm transfer via email when prompted
            ☐ Monitor transfer status
            
            After Transfer:
            ☐ Verify nameservers are correct
            ☐ Test website and email functionality
            ☐ Update auto-renewal settings
            ☐ Configure privacy protection if desired
            
            Note: Transfers typically take 5-7 days to complete.
            """;

        return PromptMessage.withUserRole(new TextContent(content));
    }

}


