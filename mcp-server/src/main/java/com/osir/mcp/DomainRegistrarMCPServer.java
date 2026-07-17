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

    @Tool(name = "loginWithDevice", description = "Start a device authorization login (RFC 8628). Returns a verificationUri and userCode. Open the URI in your browser, enter the code, and sign in with your OSIR credentials. Then call checkDeviceLoginStatus with the returned deviceCode to complete login. No parameters required.")
    public DeviceLoginResult loginWithDevice(McpConnection connection) {
        Log.infof("Starting device authorization login flow for connection %s", connection.id());
        try {
            return sessionAuthService.startDeviceLogin(connection.id());
        } catch (Exception e) {
            return new DeviceLoginResult(false, "Device login failed: " + e.getMessage());
        }
    }

    @Tool(description = "Poll for device login completion. Call this after loginWithDevice() once you have opened the verification URL and signed in. Required: deviceCode (the device_code returned by loginWithDevice). Returns authenticated=true when login is complete.")
    public DeviceLoginStatusResult checkDeviceLoginStatus(String deviceCode, McpConnection connection) {
        Log.infof("Checking device login status for connection %s", connection.id());
        try {
            return sessionAuthService.checkDeviceLoginStatus(connection.id(), deviceCode);
        } catch (Exception e) {
            return new DeviceLoginStatusResult(false, "Status check failed: " + e.getMessage(), "error");
        }
    }

    @Tool(description = "Check whether the current session is authenticated. Returns authenticated status and token expiry. No parameters required.")
    public AuthStatusResult getAuthStatus(McpConnection connection) {
        return sessionAuthService.getAuthStatus(connection.id());
    }

    @Tool(description = "Log out and clear the current session token. No parameters required.")
    public AuthResult logout(McpConnection connection) {
        return sessionAuthService.logout(connection.id());
    }

    // ── Domain Availability ───────────────────────────────────────────────────

    // Domain Availability Tools
    @Tool(description = "Check if a domain name is available for registration. Required: domain (e.g., 'example.com')")
    public DomainAvailabilityResult checkDomainAvailability(String domain, McpConnection connection) {
        mcpAuthHelper.setupAuth(connection);
        try {
            return domainService.checkAvailability(domain);
        } catch (Exception e) {
            return new DomainAvailabilityResult(domain, false, "Error checking availability: " + e.getMessage());
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
    @Tool(description = "Stage registration of a new domain name. Deducts from account balance. Required: domain (e.g., 'example.com'), years (1-10), registrantInfo (contact details), nameservers (e.g., ['ns1.example.com', 'ns2.example.com']). Optional: privacyProtection (true/false), autoRenew (true/false). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult registerDomain(
            String domain,
            int years,
            RegistrantInfo registrantInfo,
            List<String> nameservers,
            @ToolArg(required = false) Boolean privacyProtection,
            @ToolArg(required = false) Boolean autoRenew,
            McpConnection connection
    ) {
        boolean privacy = privacyProtection != null ? privacyProtection : true;
        boolean renew = autoRenew != null ? autoRenew : true;
        return pendingActionStore.stage(
                "registerDomain",
                "Register domain '" + domain + "' for " + years + " year(s) — deducts registration fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.registerDomain(domain, years, registrantInfo, nameservers, privacy, renew)
        );
    }

    // Domain Transfer Tools
    @RequiresAuth
    @Tool(description = "Stage transfer of a domain from another registrar to OSIR. Deducts from account balance. Required: domain (e.g., 'example.com'), authCode (EPP code from current registrar), registrantInfo (contact details). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult transferDomain(String domain, String authCode, RegistrantInfo registrantInfo, McpConnection connection) {
        return pendingActionStore.stage(
                "transferDomain",
                "Transfer domain '" + domain + "' to OSIR — deducts transfer fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.transferDomain(domain, authCode, registrantInfo)
        );
    }

    // Domain Management Tools
    @RequiresAuth
    @Tool(description = "Update nameservers for a domain. Required: domain (e.g., 'example.com'), nameservers (e.g., ['ns1.example.com', 'ns2.example.com'])")
    public NameserverUpdateResult updateNameservers(String domain, List<String> nameservers, McpConnection connection) {
        try {
            return domainService.updateNameservers(domain, nameservers);
        } catch (Exception e) {
            return new NameserverUpdateResult(domain, false, "Nameserver update failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get detailed information about a domain including expiration date, nameservers, and status. Required: domain (e.g., 'example.com')")
    public DomainInfoResult getDomainInfo(String domain, McpConnection connection) {
        try {
            return domainService.getDomainInfo(domain);
        } catch (Exception e) {
            return new DomainInfoResult(domain, false, "Failed to get domain info: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "List all domains owned by the authenticated user. No parameters required. Must be authenticated first.")
    public UserDomainsResult listUserDomains(McpConnection connection) {
        try {
            return domainService.getUserDomains();
        } catch (Exception e) {
            return new UserDomainsResult(false, "Failed to list domains: " + e.getMessage());
        }
    }

    // Domain Renewal
    @RequiresAuth
    @Tool(description = "Stage renewal of a domain for a specified number of years. Deducts from account balance. Required: domain (e.g., 'example.com'), years (1-10). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult renewDomain(String domain, int years, McpConnection connection) {
        return pendingActionStore.stage(
                "renewDomain",
                "Renew domain '" + domain + "' for " + years + " year(s) — deducts renewal fee from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> domainService.renewDomain(domain, years)
        );
    }

    // Domain Lock/Unlock
    @RequiresAuth
    @Tool(description = "Enable registrar lock on a domain to prevent unauthorized transfers. Required: domain (e.g., 'example.com')")
    public DomainActionResult lockDomain(String domain, McpConnection connection) {
        try {
            return domainService.lockDomain(domain);
        } catch (Exception e) {
            return new DomainActionResult(false, "Lock failed: " + e.getMessage(), domain, null);
        }
    }

    @RequiresAuth
    @Tool(description = "Stage removal of registrar lock from a domain to allow transfers. DESTRUCTIVE — reduces domain security. Required: domain (e.g., 'example.com'). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult unlockDomain(String domain, McpConnection connection) {
        return pendingActionStore.stage(
                "unlockDomain",
                "Remove registrar lock from domain '" + domain + "' — reduces security, enables transfers",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> domainService.unlockDomain(domain)
        );
    }

    // Domain Settings
    @RequiresAuth
    @Tool(description = "Enable or disable auto-renewal for a domain. Required: domain (e.g., 'example.com'), enabled (true/false)")
    public DomainActionResult updateDomainAutoRenew(String domain, boolean enabled, McpConnection connection) {
        try {
            return domainService.updateAutoRenew(domain, enabled);
        } catch (Exception e) {
            return new DomainActionResult(false, "Auto-renew update failed: " + e.getMessage(), domain, null);
        }
    }

    @RequiresAuth
    @Tool(description = "Enable or disable WHOIS privacy protection for a domain. Required: domain (e.g., 'example.com'), enabled (true/false)")
    public DomainActionResult updateDomainPrivacy(String domain, boolean enabled, McpConnection connection) {
        try {
            return domainService.updatePrivacyProtection(domain, enabled);
        } catch (Exception e) {
            return new DomainActionResult(false, "Privacy update failed: " + e.getMessage(), domain, null);
        }
    }

    // Utility Tools
    @Tool(description = "Validate if a domain name format is correct. Required: domain (e.g., 'example.com')")
    public ValidationResult validateDomainName(String domain, McpConnection connection) {
        return domainService.validateDomainName(domain);
    }

    @RequiresAuth
    @Tool(description = "Suggest alternative domain names if the requested one is unavailable (legacy method). Required: domain (e.g., 'example.com'). Optional: limit (default 10)")
    public DomainSuggestionsResult suggestAlternatives(String domain, @ToolArg(required = false) Integer limit, McpConnection connection) {
        try {
            return domainService.suggestAlternatives(domain, limit != null ? limit : 10);
        } catch (Exception e) {
            return new DomainSuggestionsResult(false, "Failed to generate suggestions: " + e.getMessage());
        }
    }

    // Domain Suggestion Tools
    @Tool(description = "Generate domain name suggestions based on keywords. Required: name (e.g., 'mycompany'). Optional: tlds (e.g., 'com,net,org'), lang ('eng'), useNumbers (true/false), maxResults (20)")
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult generateDomainSuggestions(String name, @ToolArg(required = false) String tlds, @ToolArg(required = false) String lang, @ToolArg(required = false) Boolean useNumbers, @ToolArg(required = false) Integer maxResults, McpConnection connection) {
        try {
            return domainSuggestionService.suggestDomains(name, tlds, lang, useNumbers, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate domain suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "Generate domain suggestions by spinning/replacing words. Required: name (e.g., 'pizza,restaurant'). Optional: position (0-based index), similarity (0.0-1.0), tlds ('com,net'), lang ('eng'), maxResults (20)")
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult spinDomainWords(String name, @ToolArg(required = false) Integer position, @ToolArg(required = false) Double similarity, @ToolArg(required = false) String tlds, @ToolArg(required = false) String lang, @ToolArg(required = false) Integer maxResults, McpConnection connection) {
        try {
            return domainSuggestionService.spinWord(name, position, similarity, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate word spin suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "Generate domain suggestions by adding prefixes. Required: name (e.g., 'mycompany'). Optional: vocabulary ('@prefixes' or custom), tlds ('com,net'), lang ('eng'), maxResults (20)")
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult addPrefixToDomain(String name, @ToolArg(required = false) String vocabulary, @ToolArg(required = false) String tlds, @ToolArg(required = false) String lang, @ToolArg(required = false) Integer maxResults, McpConnection connection) {
        try {
            return domainSuggestionService.addPrefix(name, vocabulary, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate prefix suggestions: " + e.getMessage());
        }
    }

    @Tool(description = "Generate domain suggestions by adding suffixes. Required: name (e.g., 'mycompany'). Optional: vocabulary ('@suffixes' or custom), tlds ('com,net'), lang ('eng'), maxResults (20)")
    public com.osir.mcp.models.suggestion.DomainSuggestionsResult addSuffixToDomain(String name, @ToolArg(required = false) String vocabulary, @ToolArg(required = false) String tlds, @ToolArg(required = false) String lang, @ToolArg(required = false) Integer maxResults, McpConnection connection) {
        try {
            return domainSuggestionService.addSuffix(name, vocabulary, tlds, lang, maxResults);
        } catch (Exception e) {
            return new com.osir.mcp.models.suggestion.DomainSuggestionsResult(false, "Failed to generate suffix suggestions: " + e.getMessage());
        }
    }

    @Tool(description = """
            Generate domain name suggestions for one or more keywords across a chosen \
            set of TLDs. Returns suggestions grouped by originating keyword.

            USAGE PATTERN:
              This is typically called AFTER listCategorizedTlds. The standard flow is:
                1. listCategorizedTlds → pick 3-6 TLDs based on the user's project
                2. bulkDomainSuggestions → find specific available names on those TLDs

            REQUIRED:
              keywords: 1-10 keywords describing the project.
              tlds: 1-6 TLDs (hard cap). Pass the TLDs chosen from listCategorizedTlds.
                    Do not include the leading dot (use "tech" not ".tech").

            OPTIONAL:
              lang: language code, default "eng".
              maxResults: max suggestions per keyword, default 20.

            Returns:
              groups: [
                { keyword: "voice", suggestions: [...] },
                { keyword: "biomarker", suggestions: [...] }
              ]
              requestedTlds: echo of the TLDs passed in.
              returnedTlds: TLDs that actually had suggestions (may be a subset).

            Note: availability per suggestion may be "available", "taken", or "unknown". \
            For "unknown" results, follow up with checkDomainAvailability on specific \
            names the user is interested in. Suggestions on premium-tier TLDs may have \
            premium pricing — confirm with checkDomainAvailability before recommending \
            to the user.""")
    public BulkDomainSuggestionsResult bulkDomainSuggestions(List<String> keywords, List<String> tlds, @ToolArg(required = false) String lang, @ToolArg(required = false) Integer maxResults, McpConnection connection) {
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

    @Tool(description = "Check keyword availability across all supported TLDs and registries with detailed results. Required: keyword (e.g., 'example'). Optional: registries ('verisign,pir,id,centralnic'), tlds ('com,net,org')")
    public Object checkKeywordAvailability(String keyword, @ToolArg(required = false) String registries, @ToolArg(required = false) String tlds, McpConnection connection) {
        try {
            return domainSuggestionService.checkKeywordAvailability(keyword, registries, tlds);
        } catch (Exception e) {
            Log.errorf(e, "Error checking keyword availability for: %s", keyword);
            return new McpError("KEYWORD_CHECK_FAILED", "Failed to check keyword availability: " + e.getMessage());
        }
    }

    @Tool(description = "Check keyword availability summary statistics without detailed domain results (faster). Required: keyword (e.g., 'example'). Optional: registries ('verisign,pir'), tlds ('com,net')")
    public Object checkKeywordAvailabilitySummary(String keyword, @ToolArg(required = false) String registries, @ToolArg(required = false) String tlds, McpConnection connection) {
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


