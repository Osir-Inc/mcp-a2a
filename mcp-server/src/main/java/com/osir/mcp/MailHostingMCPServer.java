package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.mail.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.MailService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Email hosting tools. Enabling a domain is free; mailboxes are billable (quote → confirm → create).
 * Errors are turned into user-facing messages in MailService, so tools here don't wrap exceptions.
 */
@McpAudited
@ApplicationScoped
public class MailHostingMCPServer {

    @Inject
    MailService mailService;

    @Inject
    PendingActionStore pendingActionStore;

    // Catalog / quote

    @RequiresAuth
    @Tool(description = "List available email mailbox plans with quotas and prices (monthly and annual, in cents). Requires authentication. Always quote prices from here — never from memory.")
    public MailPlanListResult listMailPlans(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listPlans();
    }

    @RequiresAuth
    @Tool(description = "Get a display-only price quote for a mailbox plan. Requires authentication. Required: packageId (from listMailPlans). Optional: term ('MONTHLY' or 'ANNUAL', default ANNUAL). The backend re-derives the authoritative price at purchase.")
    public MailQuoteResult getMailboxQuote(String packageId,
            @ToolArg(required = false) String term,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return mailService.getQuote(packageId, term);
    }

    // Domains

    @RequiresAuth
    @Tool(description = "Enable email hosting on a domain you own. Free — mailboxes are what cost money. Requires authentication. Required: domain. Optional: dnsMode ('PDNS_AUTO', the default, publishes all mail DNS records automatically — the domain must use our nameservers; 'EXTERNAL_MANUAL' returns the DNS records for you to publish at your DNS provider, and the domain stays PENDING_DNS until verifyMailDns succeeds). If the call fails with a DNS conflict (an existing SPF or MX record), ask the user for explicit consent, then re-call with spfMergeConfirmed=true (replace a foreign SPF record) and/or takeoverConfirmed=true (repoint a foreign MX — this moves their live email).")
    public MailDomainEnableResult enableMailDomain(String domain,
            @ToolArg(required = false) String dnsMode,
            @ToolArg(required = false) Boolean spfMergeConfirmed,
            @ToolArg(required = false) Boolean takeoverConfirmed,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return mailService.enableDomain(domain, dnsMode, spfMergeConfirmed, takeoverConfirmed);
    }

    @RequiresAuth
    @Tool(description = "List your domains that are enabled for email hosting, with status (PENDING_DNS or ACTIVE) and DNS mode. Requires authentication.")
    public MailDomainListResult listMailDomains(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listDomains();
    }

    @RequiresAuth
    @Tool(description = "Get the DNS records a mail domain needs (MX, SPF, DKIM, ...) — for customers managing DNS externally. Requires authentication. Required: domain.")
    public MailDnsRecordListResult getMailDnsRecords(String domain, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.getDnsRecords(domain);
    }

    @RequiresAuth
    @Tool(description = "Check that a mail domain's DNS records resolve; activates the domain for email when all records are found. Returns any still-missing records. Requires authentication. Required: domain.")
    public MailDnsVerifyResult verifyMailDns(String domain, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.verifyDns(domain);
    }

    // Mailboxes

    @RequiresAuth
    @Tool(description = "Stage creation of a paid mailbox on a mail-enabled domain. BILLABLE — deducts from account balance; get a quote with getMailboxQuote and confirm the price with the user first. Requires authentication. Required: domain (an ACTIVE mail domain), localPart (the part before the @), packageId (from listMailPlans — there is no default). Optional: term ('MONTHLY' or 'ANNUAL', default ANNUAL). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve. The result of the confirmed action contains the generated password EXACTLY ONCE — it can never be retrieved again, so show it to the user immediately; the user can change it later with setMailboxPassword. Also share the client settings from the result (IMAP/SMTP/webmail).")
    public ConfirmationRequiredResult createMailbox(String domain, String localPart, String packageId,
            @ToolArg(required = false) String term,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        String termLabel = term == null || term.isBlank() ? "ANNUAL" : term;
        return pendingActionStore.stage(
                "createMailbox",
                "Create mailbox '" + localPart + "@" + domain + "' on plan " + packageId + " (" + termLabel
                        + ") — deducts from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> mailService.createMailbox(domain, localPart, packageId, term)
        );
    }

    @RequiresAuth
    @Tool(description = "List your mailboxes with plan, payment term, status, and next renewal date. Requires authentication.")
    public MailboxListResult listMailboxes(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listMailboxes();
    }

    @RequiresAuth
    @Tool(description = "Set a new password on a mailbox. Requires authentication. Required: mailboxId (from listMailboxes), password. Never log or store the password.")
    public MailActionResult setMailboxPassword(String mailboxId, String password, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.setMailboxPassword(mailboxId, password);
    }

    @RequiresAuth
    @Tool(description = "Stage deletion of a mailbox. The mailbox stops working immediately and its data is destroyed after a 14-day grace period. Requires authentication. Required: mailboxId (from listMailboxes). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteMailbox(String mailboxId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteMailbox",
                "Delete mailbox '" + mailboxId + "' — data is destroyed after a 14-day grace period",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> mailService.deleteMailbox(mailboxId)
        );
    }

    @RequiresAuth
    @Tool(description = "Get disk usage per mailbox in bytes, for quota display alongside the plan's quotaBytes. Requires authentication.")
    public MailUsageResult getMailboxUsage(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.getUsage();
    }
}
