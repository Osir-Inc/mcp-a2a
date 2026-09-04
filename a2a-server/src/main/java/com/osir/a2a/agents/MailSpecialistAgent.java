package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.MailService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

/**
 * Email hosting specialist agent: mail domains, DNS setup and verification,
 * mailbox listing, and plan/price lookups via MailService.
 *
 * Deliberately excludes createMailbox, deleteMailbox, and setMailboxPassword.
 * Those are billable or destructive (and the mailbox password is returned
 * exactly once), and the A2A confirmation gate (docs/A2A-CONFIRMATION-GATE-SPEC.md)
 * is proposed but not implemented, so there is no staging pattern to reuse yet.
 */
@ApplicationScoped
public class MailSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(MailSpecialistAgent.class);

    private static final Set<String> SKILL_IDS = Set.of(
            "list_mail_plans", "enable_mail_domain", "list_mail_domains",
            "get_mail_dns_records", "verify_mail_dns", "list_mailboxes", "get_mailbox_quote",
            "get_mailbox_usage"
    );

    @Inject MailService mailService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "mail-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() { return SKILL_IDS; }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("email", "mail", "mailbox", "mx", "webmail", "imap", "smtp", "spf", "dkim");
    }

    @Override
    protected double getKeywordWeight() { return 0.3; }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            if ("get_mailbox_usage".equals(skill) || lower.contains("usage") || lower.contains("quota")) {
                var usage = mailService.getUsage();
                return completeWithResult(task, "mailbox-usage", usage, usage.isSuccess(),
                        usage.isSuccess() ? "Mailbox usage retrieved." : usage.getMessage());
            } else if ("list_mail_plans".equals(skill) || lower.contains("plan")) {
                return handleListPlans(task);
            } else if ("get_mailbox_quote".equals(skill) || lower.contains("quote") || lower.contains("price")) {
                return handleGetQuote(task);
            } else if ("enable_mail_domain".equals(skill) || lower.contains("enable") || lower.contains("set up")) {
                return handleEnableDomain(task, text);
            } else if ("get_mail_dns_records".equals(skill) || (lower.contains("dns") && lower.contains("record"))) {
                return handleGetDnsRecords(task, text);
            } else if ("verify_mail_dns".equals(skill) || lower.contains("verify")) {
                return handleVerifyDns(task, text);
            } else if ("list_mailboxes".equals(skill) || lower.contains("mailbox")) {
                return handleListMailboxes(task);
            } else if ("list_mail_domains".equals(skill)) {
                return handleListDomains(task);
            } else {
                return handleListDomains(task);
            }
        } catch (Exception e) {
            LOG.errorf(e, "Mail agent error: %s", e.getMessage());
            return failWithException(task, e);
        }
    }

    private A2ATask handleListPlans(A2ATask task) {
        var result = mailService.listPlans();
        return completeWithResult(task, "mail-plans", result, result.isSuccess(),
                result.isSuccess() ? "Mail plans retrieved." : result.getMessage());
    }

    private A2ATask handleGetQuote(A2ATask task) {
        String packageId = meta(task, "packageId");
        String term = meta(task, "term");
        if (packageId == null) {
            return askForInput(task,
                    "To quote a mailbox, please provide in metadata: packageId (from list_mail_plans). Optional: term (MONTHLY/ANNUAL).");
        }
        var result = mailService.getQuote(packageId, term);
        return completeWithResult(task, "mailbox-quote", result, result.isSuccess(),
                result.isSuccess() ? "Mailbox quote retrieved. Prices are display-only; the backend re-derives the price at purchase." : result.getMessage());
    }

    private A2ATask handleEnableDomain(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) {
            return askForInput(task,
                    "To enable email for a domain, please provide in metadata: domain. Optional: dnsMode, spfMergeConfirmed, takeoverConfirmed.");
        }
        var result = mailService.enableDomain(domain, meta(task, "dnsMode"),
                metaBool(task, "spfMergeConfirmed"), metaBool(task, "takeoverConfirmed"));
        return completeWithResult(task, "mail-domain", result, result.isSuccess(),
                result.getMessage());
    }

    private A2ATask handleListDomains(A2ATask task) {
        var result = mailService.listDomains();
        return completeWithResult(task, "mail-domains", result, result.isSuccess(),
                result.isSuccess() ? "Mail domains retrieved." : result.getMessage());
    }

    private A2ATask handleGetDnsRecords(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "get mail DNS records for");

        var result = mailService.getDnsRecords(domain);
        return completeWithResult(task, "mail-dns-records", result, result.isSuccess(),
                result.isSuccess() ? "Mail DNS records retrieved. Publish these at the DNS provider, then verify." : result.getMessage());
    }

    private A2ATask handleVerifyDns(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "verify mail DNS for");

        var result = mailService.verifyDns(domain);
        return completeWithResult(task, "mail-dns-verify", result, result.isSuccess(),
                result.getMessage());
    }

    private A2ATask handleListMailboxes(A2ATask task) {
        // MailService lists all mailboxes for the account; no per-domain filter exists.
        var result = mailService.listMailboxes();
        return completeWithResult(task, "mailboxes", result, result.isSuccess(),
                result.isSuccess() ? "Mailboxes retrieved." : result.getMessage());
    }

    private Boolean metaBool(A2ATask task, String key) {
        String v = meta(task, key);
        return v != null ? Boolean.parseBoolean(v) : null;
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Email Agent");
        card.setDescription("Manages email hosting on the OSIR platform: mail domains, DNS setup and verification, mailboxes, and mail plans.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("list_mail_plans", "List Mail Plans",
                        "List available mailbox hosting plans",
                        List.of("email", "plans", "pricing"),
                        List.of("What email plans do you offer?", "Show me the mailbox plans")),
                new Skill("enable_mail_domain", "Enable Mail Domain",
                        "Enable email hosting for a domain the user owns",
                        List.of("email", "domain", "enable"),
                        List.of("Enable email for example.com", "Set up email hosting on my domain")),
                new Skill("list_mail_domains", "List Mail Domains",
                        "List domains enabled for email hosting",
                        List.of("email", "domains", "list"),
                        List.of("Which of my domains have email enabled?")),
                new Skill("get_mail_dns_records", "Get Mail DNS Records",
                        "Get the DNS records to publish for a mail domain (MX, SPF, DKIM)",
                        List.of("email", "dns", "mx", "spf"),
                        List.of("What DNS records do I need for email on example.com?")),
                new Skill("verify_mail_dns", "Verify Mail DNS",
                        "Trigger DNS verification for a mail domain",
                        List.of("email", "dns", "verify"),
                        List.of("Verify the mail DNS for example.com", "Check if my email DNS is set up")),
                new Skill("list_mailboxes", "List Mailboxes",
                        "List the mailboxes on the account",
                        List.of("email", "mailbox", "list"),
                        List.of("List my mailboxes", "Show my email accounts")),
                new Skill("get_mailbox_quote", "Get Mailbox Quote",
                        "Get a price quote for a mailbox plan",
                        List.of("email", "mailbox", "quote", "pricing"),
                        List.of("How much does a mailbox cost per year?")),
                new Skill("get_mailbox_usage", "Get Mailbox Usage",
                        "Storage used by each mailbox on the account",
                        List.of("email", "mailbox", "usage", "quota"),
                        List.of("How much space are my mailboxes using?",
                                "Is any mailbox close to its quota?"))
        ));
        return card;
    }
}
