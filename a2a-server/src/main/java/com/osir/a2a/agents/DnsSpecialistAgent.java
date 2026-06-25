package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
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
        return Set.of("list_dns_records", "create_dns_record", "update_dns_record", "delete_dns_record", "get_dns_record");
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

            if ("list_dns_records".equals(skill) || lower.contains("list") || lower.contains("show")) {
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
        var result = dnsService.deleteRecord(domain, m.group(1));
        task.addMessage(new Message("agent", result.isSuccess() ? "DNS record deleted." : result.getMessage()));
        task.transitionTo(result.isSuccess() ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
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
                new Skill("list_dns_records", "List DNS Records", "List all DNS records for a domain"),
                new Skill("create_dns_record", "Create DNS Record", "Create A, AAAA, CNAME, MX, TXT, or SRV records"),
                new Skill("update_dns_record", "Update DNS Record", "Update an existing DNS record"),
                new Skill("delete_dns_record", "Delete DNS Record", "Delete a DNS record"),
                new Skill("get_dns_record", "Get DNS Record", "Get details of a specific DNS record")
        ));
        return card;
    }
}
