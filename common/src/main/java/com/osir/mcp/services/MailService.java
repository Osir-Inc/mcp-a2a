package com.osir.mcp.services;

import com.osir.mcp.clients.MailBackendClient;
import com.osir.mcp.models.mail.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

/**
 * Email hosting: mail domains (free) and paid mailboxes. Mirrors VpsService's shape.
 *
 * Error contract from the backend (docs/mail-mcp-tools.md in domain-registrar):
 *  - 400 "Email hosting is not enabled" = feature flag off — report "not available", not an error.
 *  - 402 on mailbox create = insufficient balance — point at top-up, not a retry.
 *  - 409 on domain enable = existing SPF/MX conflict — re-call with the matching confirm flag
 *    after the user explicitly consents.
 */
@ApplicationScoped
public class MailService {

    private static final Logger LOG = Logger.getLogger(MailService.class);

    private static final String AUTH_REQUIRED = "Authentication required. Please use loginWithDevice to authenticate.";
    private static final String NOT_AVAILABLE = "Email hosting is not available on this platform.";

    /** Mail client settings, surfaced after every mailbox create per the tool contract. */
    static final String CLIENT_SETTINGS =
            "IMAP: mx1.osir.com:993 (SSL) | POP3: mx1.osir.com:995 (SSL) | SMTP: mx1.osir.com:465 (SSL) | "
            + "username = full email address | Webmail: https://webmail.osir.com:8443 | "
            + "Thunderbird/Apple Mail/Outlook auto-configure.";

    @Inject
    @RestClient
    MailBackendClient backendClient;

    @Inject
    AuthService authService;

    public MailPlanListResult listPlans() {
        if (!authService.isAuthenticated()) {
            return new MailPlanListResult(false, AUTH_REQUIRED);
        }
        try {
            List<MailPlan> plans = backendClient.getPlans(authService.getCurrentToken());
            MailPlanListResult result = new MailPlanListResult(true, "Mail plans retrieved successfully");
            result.setPlans(plans);
            return result;
        } catch (Exception e) {
            return new MailPlanListResult(false, describeError("Failed to list mail plans", e));
        }
    }

    public MailQuoteResult getQuote(String packageId, String term) {
        if (!authService.isAuthenticated()) {
            return new MailQuoteResult(false, AUTH_REQUIRED);
        }
        try {
            MailQuoteResult result = backendClient.getQuote(packageId, normalizeTerm(term), authService.getCurrentToken());
            result.setSuccess(true);
            result.setMessage("Quote retrieved. Display-only — the backend re-derives the price at purchase.");
            return result;
        } catch (Exception e) {
            return new MailQuoteResult(false, describeError("Failed to get mailbox quote", e));
        }
    }

    public MailDomainEnableResult enableDomain(String domain, String dnsMode,
                                               Boolean spfMergeConfirmed, Boolean takeoverConfirmed) {
        if (!authService.isAuthenticated()) {
            return new MailDomainEnableResult(false, AUTH_REQUIRED);
        }
        try {
            MailEnableDomainRequest request = new MailEnableDomainRequest(
                    dnsMode == null || dnsMode.isBlank() ? null : dnsMode.trim().toUpperCase(java.util.Locale.ROOT),
                    Boolean.TRUE.equals(spfMergeConfirmed),
                    Boolean.TRUE.equals(takeoverConfirmed));
            MailDomainEnableResult result = backendClient.enableDomain(domain, request, authService.getCurrentToken());
            result.setSuccess(true);
            boolean pending = result.getDomain() != null && "PENDING_DNS".equals(result.getDomain().getStatus());
            result.setMessage(pending
                    ? "Domain provisioned for email. Publish the returned DNS records at your DNS provider, then call verifyMailDns."
                    : "Domain provisioned for email and DNS published automatically.");
            return result;
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 409) {
                return new MailDomainEnableResult(false,
                        "DNS conflict: " + readErrorMessage(e.getResponse())
                        + " If the user explicitly confirms, re-call enableMailDomain with spfMergeConfirmed=true "
                        + "(replace a foreign SPF record) and/or takeoverConfirmed=true (repoint a foreign MX — moves live email).");
            }
            return new MailDomainEnableResult(false, describeError("Failed to enable mail domain", e));
        } catch (Exception e) {
            return new MailDomainEnableResult(false, describeError("Failed to enable mail domain", e));
        }
    }

    public MailDomainListResult listDomains() {
        if (!authService.isAuthenticated()) {
            return new MailDomainListResult(false, AUTH_REQUIRED);
        }
        try {
            List<MailDomainInfo> domains = backendClient.getDomains(authService.getCurrentToken());
            MailDomainListResult result = new MailDomainListResult(true, "Mail domains retrieved successfully");
            result.setDomains(domains);
            return result;
        } catch (Exception e) {
            return new MailDomainListResult(false, describeError("Failed to list mail domains", e));
        }
    }

    public MailDnsRecordListResult getDnsRecords(String domain) {
        if (!authService.isAuthenticated()) {
            return new MailDnsRecordListResult(false, AUTH_REQUIRED);
        }
        try {
            List<MailDnsRecordInfo> records = backendClient.getDnsRecords(domain, authService.getCurrentToken());
            MailDnsRecordListResult result = new MailDnsRecordListResult(true,
                    "DNS records for " + domain + " retrieved successfully");
            result.setRecords(records);
            return result;
        } catch (Exception e) {
            return new MailDnsRecordListResult(false, describeError("Failed to get mail DNS records", e));
        }
    }

    public MailDnsVerifyResult verifyDns(String domain) {
        if (!authService.isAuthenticated()) {
            return new MailDnsVerifyResult(false, AUTH_REQUIRED);
        }
        try {
            MailDnsVerifyResult result = backendClient.verifyDns(domain, authService.getCurrentToken());
            result.setSuccess(true);
            result.setMessage(Boolean.TRUE.equals(result.getVerified())
                    ? "All DNS records found — the domain is now active for email."
                    : "Some DNS records are still missing (see 'missing'). Publish them and verify again; DNS changes can take time to propagate.");
            return result;
        } catch (Exception e) {
            return new MailDnsVerifyResult(false, describeError("Failed to verify mail DNS", e));
        }
    }

    public MailboxCreateResult createMailbox(String domain, String localPart, String packageId, String term) {
        if (!authService.isAuthenticated()) {
            return new MailboxCreateResult(false, AUTH_REQUIRED);
        }
        try {
            MailboxCreateRequest request = new MailboxCreateRequest(localPart, packageId, normalizeTerm(term));
            MailboxCreateResult result = backendClient.createMailbox(domain, request, authService.getCurrentToken());
            result.setSuccess(true);
            String email = (result.getAccount() != null ? result.getAccount().getLocalPart() : localPart)
                    + "@" + domain;
            result.setEmailAddress(email);
            result.setClientSettings(CLIENT_SETTINGS);
            result.setMessage("Mailbox " + email + " created. IMPORTANT: the generated password is returned "
                    + "exactly once and can never be retrieved again — show it to the user immediately.");
            return result;
        } catch (WebApplicationException e) {
            if (e.getResponse() != null && e.getResponse().getStatus() == 402) {
                return new MailboxCreateResult(false,
                        "Insufficient account balance: " + readErrorMessage(e.getResponse())
                        + " Top up first (createPaymentSession), then try again.");
            }
            return new MailboxCreateResult(false, describeError("Failed to create mailbox", e));
        } catch (Exception e) {
            return new MailboxCreateResult(false, describeError("Failed to create mailbox", e));
        }
    }

    public MailboxListResult listMailboxes() {
        if (!authService.isAuthenticated()) {
            return new MailboxListResult(false, AUTH_REQUIRED);
        }
        try {
            List<MailboxSummary> mailboxes = backendClient.getMailboxes(authService.getCurrentToken());
            MailboxListResult result = new MailboxListResult(true, "Mailboxes retrieved successfully");
            result.setMailboxes(mailboxes);
            result.setTotalCount(mailboxes != null ? mailboxes.size() : 0);
            return result;
        } catch (Exception e) {
            return new MailboxListResult(false, describeError("Failed to list mailboxes", e));
        }
    }

    public MailActionResult setMailboxPassword(String mailboxId, String password) {
        if (!authService.isAuthenticated()) {
            return new MailActionResult(false, AUTH_REQUIRED);
        }
        try {
            backendClient.setPassword(mailboxId, new MailPasswordRequest(password), authService.getCurrentToken());
            return new MailActionResult(true, "Mailbox password updated.");
        } catch (Exception e) {
            return new MailActionResult(false, describeError("Failed to set mailbox password", e));
        }
    }

    public MailActionResult deleteMailbox(String mailboxId) {
        if (!authService.isAuthenticated()) {
            return new MailActionResult(false, AUTH_REQUIRED);
        }
        try {
            backendClient.deleteMailbox(mailboxId, authService.getCurrentToken());
            return new MailActionResult(true,
                    "Mailbox scheduled for deletion. It enters a 14-day grace period before the data is destroyed.");
        } catch (Exception e) {
            return new MailActionResult(false, describeError("Failed to delete mailbox", e));
        }
    }

    public MailUsageResult getUsage() {
        if (!authService.isAuthenticated()) {
            return new MailUsageResult(false, AUTH_REQUIRED);
        }
        try {
            Map<String, Long> usage = backendClient.getUsage(authService.getCurrentToken());
            MailUsageResult result = new MailUsageResult(true, "Mailbox disk usage retrieved (bytes per address)");
            result.setUsage(usage);
            return result;
        } catch (Exception e) {
            return new MailUsageResult(false, describeError("Failed to get mailbox usage", e));
        }
    }

    /** Backend PaymentTerm for mail is MONTHLY | ANNUAL; be liberal in what we accept from LLM callers. */
    private static String normalizeTerm(String term) {
        return term == null || term.isBlank() ? null
                : term.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    /**
     * Turns an exception into a user-facing message: surfaces the backend's {"error": ...} instead of
     * "Bad Request, status code 400", and maps the feature-flag 400 to a calm "not available".
     * Logs here so callers don't have to; never logs request bodies (mailbox passwords).
     */
    private String describeError(String prefix, Exception e) {
        if (e instanceof WebApplicationException wae && wae.getResponse() != null) {
            String detail = readErrorMessage(wae.getResponse());
            if (detail.toLowerCase(java.util.Locale.ROOT).contains("not enabled")) {
                return NOT_AVAILABLE;
            }
            LOG.errorf("%s: %s", prefix, detail);
            return prefix + ": " + detail;
        }
        LOG.errorf(e, "%s: %s", prefix, e.getMessage());
        return prefix + ": " + e.getMessage();
    }

    /** Pulls the backend's {"error": "..."} out of a failed response, falling back to the status line. */
    private String readErrorMessage(jakarta.ws.rs.core.Response response) {
        if (response == null) {
            return "unknown error";
        }
        try {
            String body = response.readEntity(String.class);
            if (body != null && body.contains("\"error\"")) {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                if (node.hasNonNull("error")) {
                    return node.get("error").asText();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic message below.
        }
        return "HTTP " + response.getStatus();
    }
}
