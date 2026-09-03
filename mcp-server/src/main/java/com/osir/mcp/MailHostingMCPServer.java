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
    @Tool(description = "List available email mailbox plans with quotas and prices (monthly and annual, in cents). Requires authentication. Always quote prices from here, never from memory.",
            annotations = @Tool.Annotations(
                    title = "List mail plans",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailPlanListResult listMailPlans(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listPlans();
    }

    @RequiresAuth
    @Tool(description = "Get a display-only price quote for a mailbox plan. The backend re-derives the authoritative price at purchase. Requires authentication.",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get mailbox quote",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailQuoteResult getMailboxQuote(
            @ToolArg(description = "Mailbox plan id from listMailPlans.") String packageId,
            @ToolArg(required = false, description = "Payment term: 'MONTHLY' or 'ANNUAL' (default ANNUAL).") String term,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return mailService.getQuote(packageId, term);
    }

    // Domains

    @RequiresAuth
    @Tool(description = "Enable email hosting on a domain you own. Free; mailboxes are what cost money. If the call fails with a DNS conflict (an existing SPF or MX record), ask the user for explicit consent, then re-call with spfMergeConfirmed=true and/or takeoverConfirmed=true. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Enable mail domain",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailDomainEnableResult enableMailDomain(
            @ToolArg(description = "The domain to enable email hosting on, e.g. 'example.com'.") String domain,
            @ToolArg(required = false, description = "'PDNS_AUTO' (default) publishes all mail DNS records automatically; the domain must use our nameservers. 'EXTERNAL_MANUAL' returns the DNS records for you to publish at your DNS provider, and the domain stays PENDING_DNS until verifyMailDns succeeds.") String dnsMode,
            @ToolArg(required = false, description = "Set true, only with the user's explicit consent, to replace a foreign SPF record.") Boolean spfMergeConfirmed,
            @ToolArg(required = false, description = "Set true, only with the user's explicit consent, to repoint a foreign MX record; this moves their live email.") Boolean takeoverConfirmed,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return mailService.enableDomain(domain, dnsMode, spfMergeConfirmed, takeoverConfirmed);
    }

    @RequiresAuth
    @Tool(description = "List your domains that are enabled for email hosting, with status (PENDING_DNS or ACTIVE) and DNS mode. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List mail domains",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailDomainListResult listMailDomains(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listDomains();
    }

    @RequiresAuth
    @Tool(description = "Get the DNS records a mail domain needs (MX, SPF, DKIM, ...), for customers managing DNS externally. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get mail DNS records",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailDnsRecordListResult getMailDnsRecords(
            @ToolArg(description = "A mail-enabled domain from listMailDomains, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.getDnsRecords(domain);
    }

    @RequiresAuth
    @Tool(description = "Check that a mail domain's DNS records resolve; activates the domain for email when all records are found. Returns any still-missing records. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Verify mail DNS",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailDnsVerifyResult verifyMailDns(
            @ToolArg(description = "The mail-enabled domain to verify, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.verifyDns(domain);
    }

    // Mailboxes

    @RequiresAuth
    @Tool(description = "Stage creation of a paid mailbox on a mail-enabled domain. BILLABLE: deducts from account balance; get a quote with getMailboxQuote and confirm the price with the user first. Requires authentication. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve. The result of the confirmed action contains the generated password EXACTLY ONCE; it can never be retrieved again, so show it to the user immediately (they can change it later with setMailboxPassword). Also share the client settings from the result (IMAP/SMTP/webmail).",
            annotations = @Tool.Annotations(
                    title = "Create mailbox",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult createMailbox(
            @ToolArg(description = "An ACTIVE mail-enabled domain from listMailDomains.") String domain,
            @ToolArg(description = "The part of the address before the @; the full mailbox address becomes 'localPart@domain', e.g. 'user@example.com'.") String localPart,
            @ToolArg(description = "Mailbox plan id from listMailPlans; there is no default.") String packageId,
            @ToolArg(required = false, description = "Payment term: 'MONTHLY' or 'ANNUAL' (default ANNUAL).") String term,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        String termLabel = term == null || term.isBlank() ? "ANNUAL" : term;
        return pendingActionStore.stage(
                "createMailbox",
                "Create mailbox '" + localPart + "@" + domain + "' on plan " + packageId + " (" + termLabel
                        + "), deducts from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> mailService.createMailbox(domain, localPart, packageId, term)
        );
    }

    @RequiresAuth
    @Tool(description = "List your mailboxes with plan, payment term, status, and next renewal date. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List mailboxes",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailboxListResult listMailboxes(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.listMailboxes();
    }

    @RequiresAuth
    @Tool(description = "Set a new password on a mailbox. Never log or store the password. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Set mailbox password",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailActionResult setMailboxPassword(
            @ToolArg(description = "Mailbox id from listMailboxes.") String mailboxId,
            @ToolArg(description = "The new mailbox password; never log or store it.") String password,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.setMailboxPassword(mailboxId, password);
    }

    @RequiresAuth
    @Tool(description = "Stage deletion of a mailbox. The mailbox stops working immediately and its data is destroyed after a 14-day grace period. Requires authentication. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Delete mailbox",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult deleteMailbox(
            @ToolArg(description = "Mailbox id from listMailboxes.") String mailboxId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteMailbox",
                "Delete mailbox '" + mailboxId + "', data is destroyed after a 14-day grace period",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> mailService.deleteMailbox(mailboxId)
        );
    }

    @RequiresAuth
    @Tool(description = "Get disk usage per mailbox in bytes, for quota display alongside the plan's quotaBytes. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get mailbox usage",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public MailUsageResult getMailboxUsage(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return mailService.getUsage();
    }
}
