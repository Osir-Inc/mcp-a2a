package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.a2a.security.ConfirmationGate;
import com.osir.mcp.security.DestructiveOpRateLimiter.Bucket;
import com.osir.mcp.services.DnsService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class DnsSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(DnsSpecialistAgent.class);

    @Inject DnsService dnsService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "dns-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("list_dns_records", "create_dns_record", "update_dns_record", "delete_dns_record",
                "get_dns_record", "initialize_dns_zone", ConfirmationGate.CONFIRM_SKILL);
    }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("dns", "record", "a record", "aaaa", "cname", "mx", "txt", "srv", "ns record");
    }

    @Override
    protected double getKeywordWeight() { return 0.3; }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            // Before the keyword chain: an actionId confirms a staged delete rather than re-staging it.
            if (isConfirming(task)) {
                return runConfirmed(task);
            }

            if ("initialize_dns_zone".equals(skill)) {
                return handleInitializeZone(task, text);
            } else if ("list_dns_records".equals(skill) || lower.contains("list") || lower.contains("show")) {
                return handleListRecords(task, text);
            } else if ("create_dns_record".equals(skill) || lower.contains("create") || lower.contains("add")) {
                return handleCreateRecord(task, text);
            } else if ("update_dns_record".equals(skill) || lower.contains("update") || lower.contains("edit")) {
                return handleUpdateRecord(task, text);
            } else if ("get_dns_record".equals(skill)) {
                return handleGetRecord(task, text);
            } else if ("delete_dns_record".equals(skill) || lower.contains("delete") || lower.contains("remove")) {
                return handleDeleteRecord(task, text);
            } else {
                return handleListRecords(task, text);
            }
        } catch (Exception e) {
            LOG.errorf(e, "DNS agent error: %s", e.getMessage());
            return failWithError(task, e.getMessage());
        }
    }

    private A2ATask handleListRecords(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "list DNS records for");

        var result = dnsService.listRecords(domain);
        return completeWithResult(task, "dns-records", result, result.isSuccess(),
                result.isSuccess() ? "DNS records retrieved." : result.getMessage());
    }

    private A2ATask handleCreateRecord(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        String name = meta(task, "name");
        String type = meta(task, "type");
        String content = meta(task, "content");
        Integer ttl = metaInt(task, "ttl");
        Integer priority = metaInt(task, "priority");

        if (domain == null || name == null || type == null || content == null) {
            return askForInput(task,
                    "To create a DNS record, please provide in metadata: domain, name, type (A/AAAA/CNAME/MX/TXT), content. Optional: ttl, priority.");
        }
        var result = dnsService.createRecord(domain, name, type, content, ttl, priority);
        return completeWithResult(task, "dns-record", result, result.isSuccess(),
                result.isSuccess() ? "DNS record created." : result.getMessage());
    }

    private A2ATask handleUpdateRecord(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        String recordId = meta(task, "recordId");

        if (domain == null || recordId == null) {
            return askForInput(task,
                    "To update a DNS record, please provide in metadata: domain, recordId. Optional: name, type, content, ttl, priority.");
        }
        var result = dnsService.updateRecord(domain, recordId,
                meta(task, "name"), meta(task, "type"), meta(task, "content"),
                metaInt(task, "ttl"), metaInt(task, "priority"));
        return completeWithResult(task, "dns-record", result, result.isSuccess(),
                result.isSuccess() ? "DNS record updated." : result.getMessage());
    }

    private A2ATask handleGetRecord(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        String recordId = meta(task, "recordId");

        if (domain == null || recordId == null) {
            return askForInput(task, "Please provide in metadata: domain and recordId to retrieve the DNS record.");
        }
        var result = dnsService.getRecord(domain, recordId);
        return completeWithResult(task, "dns-record", result, result.isSuccess(),
                result.isSuccess() ? "DNS record retrieved." : result.getMessage());
    }

    private A2ATask handleDeleteRecord(A2ATask task, String text) {
        String domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "delete DNS records from");

        Pattern idPattern = Pattern.compile("(?:record|id)\\s*[:#]?\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher m = idPattern.matcher(text);
        if (!m.find()) {
            task.transitionTo(TaskState.INPUT_REQUIRED);
            task.addMessage(new Message("agent",
                    "Please provide the record ID to delete from " + domain + ". " +
                    "Use 'list DNS records for " + domain + "' to see available records."));
            return task;
        }
        String recordId = m.group(1);
        return stage(task, "delete_dns_record", java.util.Map.of("domain", domain, "recordId", recordId),
                "Delete DNS record " + recordId + " from " + domain + ". Removing a live record can take "
                        + "the site or its mail offline, and it cannot be undone from here.",
                Bucket.DESTRUCTIVE);
    }

    /** Run what was staged, from the parameters frozen at stage time. */
    private A2ATask runConfirmed(A2ATask task) {
        var claim = confirmationGate.claim(task);
        if (!claim.ok()) {
            return failWithError(task, claim.error());
        }
        java.util.Map<String, Object> p = claim.action().params();
        if (!"delete_dns_record".equals(claim.action().skill())) {
            return failWithError(task, "Cannot run '" + claim.action().skill() + "': unknown staged action.");
        }
        var result = dnsService.deleteRecord(String.valueOf(p.get("domain")), String.valueOf(p.get("recordId")));
        return completeWithResult(task, "dns-record-delete", result, result.isSuccess(),
                result.isSuccess() ? "DNS record deleted." : result.getMessage());
    }

    /** Create the zone itself. Idempotent and free; without it every record call answers "zone not found". */
    private A2ATask handleInitializeZone(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) return askForDomain(task, "initialize a DNS zone for");

        var result = dnsService.initializeZone(domain);
        return completeWithResult(task, "dns-zone", result, result.isSuccess(),
                result.isSuccess() ? "DNS zone initialized for " + domain + "." : result.getMessage());
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR DNS Agent");
        card.setDescription("Manages DNS records for domains on the OSIR platform.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("list_dns_records", "List DNS Records", "List all DNS records for a domain",
                        List.of("dns", "records", "list"),
                        List.of("List the DNS records for cedarloop.com", "Show me the zone for brahaj.al")),
                new Skill("create_dns_record", "Create DNS Record", "Create A, AAAA, CNAME, MX, TXT, or SRV records",
                        List.of("dns", "records", "create"),
                        List.of("Add an A record for www.cedarloop.com pointing to 203.0.113.42",
                                "Create an MX record for brahaj.al with priority 10")),
                new Skill("update_dns_record", "Update DNS Record", "Update an existing DNS record",
                        List.of("dns", "records", "update"),
                        List.of("Change the A record on cedarloop.com to 203.0.113.99",
                                "Update the TTL on record 5512 to 300")),
                new Skill("delete_dns_record", "Delete DNS Record", "Delete a DNS record",
                        List.of("dns", "records", "delete"),
                        List.of("Delete record 5512 from cedarloop.com", "Remove the old TXT record on brahaj.al")),
                new Skill("get_dns_record", "Get DNS Record", "Get details of a specific DNS record",
                        List.of("dns", "records", "details"),
                        List.of("Show me record 5512 on cedarloop.com")),
                new Skill(ConfirmationGate.CONFIRM_SKILL, "Confirm A Staged Action",
                        "Run an action this agent staged: send the actionId it returned, on the same task",
                        List.of("confirmation", "safety"),
                        List.of("Confirm action a2a_1f4c... on this task", "Yes, delete that record")),
                new Skill("initialize_dns_zone", "Initialize DNS Zone",
                        "Create the DNS zone for a domain hosted on OSIR nameservers (idempotent, free)",
                        List.of("dns", "zone", "setup"),
                        List.of("Set up DNS hosting for cedarloop.com",
                                "Create the zone for brahaj.al so I can add records"))
        ));
        return card;
    }
}
