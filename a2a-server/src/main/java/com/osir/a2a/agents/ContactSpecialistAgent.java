package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.mcp.services.ContactService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ContactSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(ContactSpecialistAgent.class);

    @Inject ContactService contactService;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "contact-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("list_contacts", "get_contact", "create_contact", "update_contact", "delete_contact", "get_domain_contacts");
    }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("contact", "registrant", "admin contact", "tech contact", "billing contact", "whois contact");
    }

    @Override
    protected double getKeywordWeight() { return 0.3; }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            if ("list_contacts".equals(skill) || lower.contains("list") || lower.contains("show") || lower.contains("all")) {
                var result = contactService.listContacts(null);
                return completeWithResult(task, "contacts", result, result.isSuccess(),
                        result.isSuccess() ? "Contacts retrieved." : result.getMessage());
            } else if ("create_contact".equals(skill) || lower.contains("create") || lower.contains("add") || lower.contains("new")) {
                String firstName = meta(task, "firstName");
                String lastName = meta(task, "lastName");
                String email = meta(task, "email");
                String phone = meta(task, "phone");
                String street1 = meta(task, "street1");
                String city = meta(task, "city");
                String postalCode = meta(task, "postalCode");
                String country = meta(task, "country");
                if (firstName == null || lastName == null || email == null || phone == null
                        || street1 == null || city == null || postalCode == null || country == null) {
                    return askForInput(task,
                            "To create a contact, please provide in metadata: firstName, lastName, email, phone, " +
                            "street1, city, postalCode, country. Optional: organization, street2, state.");
                }
                var result = contactService.createContact(firstName, lastName, email, phone,
                        meta(task, "organization"), street1, meta(task, "street2"),
                        city, meta(task, "state"), postalCode, country);
                return completeWithResult(task, "contact", result, result.isSuccess(),
                        result.isSuccess() ? "Contact created." : result.getMessage());
            } else if ("get_contact".equals(skill)) {
                String contactId = meta(task, "contactId");
                if (contactId == null) return askForInput(task, "Please provide the contact ID in metadata.");
                var result = contactService.getContact(contactId);
                return completeWithResult(task, "contact", result, result.isSuccess(),
                        result.isSuccess() ? "Contact retrieved." : result.getMessage());
            } else if ("update_contact".equals(skill)) {
                String contactId = meta(task, "contactId");
                if (contactId == null) return askForInput(task, "Please provide the contact ID in metadata to update.");
                var result = contactService.updateContact(contactId,
                        meta(task, "firstName"), meta(task, "lastName"), meta(task, "email"),
                        meta(task, "phone"), meta(task, "organization"), meta(task, "street1"),
                        meta(task, "street2"), meta(task, "city"), meta(task, "state"),
                        meta(task, "postalCode"), meta(task, "country"));
                return completeWithResult(task, "contact", result, result.isSuccess(),
                        result.isSuccess() ? "Contact updated." : result.getMessage());
            } else if ("get_domain_contacts".equals(skill) || (lower.contains("domain") && lower.contains("contact"))) {
                String domain = meta(task, "domain");
                if (domain == null) domain = extractDomain(text);
                if (domain == null) return askForInput(task, "Please provide the domain name (e.g., 'example.com') to list its assigned contacts.");
                var result = contactService.getContactsForDomain(domain);
                return completeWithResult(task, "domain-contacts", result, result.isSuccess(),
                        result.isSuccess() ? "Domain contacts retrieved." : result.getMessage());
            } else if ("delete_contact".equals(skill) || lower.contains("delete") || lower.contains("remove")) {
                String contactId = meta(task, "contactId");
                if (contactId == null) return askForInput(task, "Please provide the contact ID to delete.");
                var result = contactService.deleteContact(contactId);
                return completeWithResult(task, "contact-delete", result, result.isSuccess(),
                        result.isSuccess() ? "Contact deleted." : result.getMessage());
            } else {
                var result = contactService.listContacts(null);
                return completeWithResult(task, "contacts", result, result.isSuccess(), "Here are your contacts.");
            }
        } catch (Exception e) {
            LOG.errorf(e, "Contact agent error: %s", e.getMessage());
            return failWithError(task, e.getMessage());
        }
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Contact Agent");
        card.setDescription("Manages registrant and domain contact records.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("list_contacts", "List Contacts", "List all contacts"),
                new Skill("get_contact", "Get Contact", "Get details of a specific contact"),
                new Skill("create_contact", "Create Contact", "Create a new contact record"),
                new Skill("update_contact", "Update Contact", "Update an existing contact"),
                new Skill("delete_contact", "Delete Contact", "Delete a contact"),
                new Skill("get_domain_contacts", "Get Domain Contacts", "Get contacts assigned to a domain — provide domain name e.g. example.com")
        ));
        return card;
    }
}
