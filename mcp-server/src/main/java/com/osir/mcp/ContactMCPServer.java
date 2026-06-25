package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.contact.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.ContactService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class ContactMCPServer {

    @Inject
    ContactService contactService;

    @Inject
    PendingActionStore pendingActionStore;

    @Tool(description = "List all contacts for the authenticated user with optional search. Requires authentication. Optional: search (search by name/email/org)")
    public ContactListResult listContacts(@ToolArg(required = false) String search, McpConnection connection) {
        try {
            return contactService.listContacts(search);
        } catch (Exception e) {
            Log.errorf(e, "Error listing contacts: %s", e.getMessage());
            return new ContactListResult(false, "Failed to list contacts: " + e.getMessage());
        }
    }

    @Tool(description = "Get detailed information about a specific contact. Requires authentication. Required: contactId (string)")
    public ContactDetailResult getContact(String contactId, McpConnection connection) {
        try {
            return contactService.getContact(contactId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting contact: %s", e.getMessage());
            return new ContactDetailResult(false, "Failed to get contact: " + e.getMessage());
        }
    }

    @Tool(description = "Create a new contact for use with domain registrations. Requires authentication. Required: firstName, lastName, email, phone (E.164 format), street1, city, postalCode, country (ISO 3166-1 alpha-2). Optional: organization, street2, state")
    public ContactResult createContact(String firstName, String lastName, String email, String phone,
                                        @ToolArg(required = false) String organization, String street1, @ToolArg(required = false) String street2,
                                        String city, @ToolArg(required = false) String state, String postalCode, String country,
                                        McpConnection connection) {
        try {
            return contactService.createContact(firstName, lastName, email, phone, organization,
                    street1, street2, city, state, postalCode, country);
        } catch (Exception e) {
            Log.errorf(e, "Error creating contact: %s", e.getMessage());
            return new ContactResult(false, "Failed to create contact: " + e.getMessage());
        }
    }

    @Tool(description = "Update an existing contact's information. Requires authentication. Required: contactId (string). Optional: firstName, lastName, email, phone, organization, street1, street2, city, state, postalCode, country")
    public ContactResult updateContact(String contactId, @ToolArg(required = false) String firstName, @ToolArg(required = false) String lastName, @ToolArg(required = false) String email,
                                        @ToolArg(required = false) String phone, @ToolArg(required = false) String organization, @ToolArg(required = false) String street1, @ToolArg(required = false) String street2,
                                        @ToolArg(required = false) String city, @ToolArg(required = false) String state, @ToolArg(required = false) String postalCode, @ToolArg(required = false) String country,
                                        McpConnection connection) {
        try {
            return contactService.updateContact(contactId, firstName, lastName, email, phone, organization,
                    street1, street2, city, state, postalCode, country);
        } catch (Exception e) {
            Log.errorf(e, "Error updating contact: %s", e.getMessage());
            return new ContactResult(false, "Failed to update contact: " + e.getMessage());
        }
    }

    @Tool(description = "Stage deletion of a contact. DESTRUCTIVE — cannot delete if assigned to active domains. Requires authentication. Required: contactId (string). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteContact(String contactId, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteContact",
                "Permanently delete contact '" + contactId + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> contactService.deleteContact(contactId)
        );
    }

    @Tool(description = "Get all contacts (registrant, admin, tech, billing) assigned to a domain. Requires authentication. Required: domain (e.g., 'example.com')")
    public DomainContactsResult getContactsForDomain(String domain, McpConnection connection) {
        try {
            return contactService.getContactsForDomain(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain contacts: %s", e.getMessage());
            return new DomainContactsResult(false, "Failed to get domain contacts: " + e.getMessage());
        }
    }
}
