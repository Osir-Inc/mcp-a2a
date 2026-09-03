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

    @Tool(description = "listContacts: List all contacts for the authenticated user, optionally filtered by a search term. Requires authentication. Returns each contact with its id for use in getContact, updateContact, deleteContact, or domain registration.",
            annotations = @Tool.Annotations(
                    title = "List contacts",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ContactListResult listContacts(
            @ToolArg(required = false, description = "Optional search term matched against contact name, email, or organization.") String search,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return contactService.listContacts(search);
        } catch (Exception e) {
            Log.errorf(e, "Error listing contacts: %s", e.getMessage());
            return new ContactListResult(false, "Failed to list contacts: " + e.getMessage());
        }
    }

    @Tool(description = "getContact: Get detailed information about a specific contact. Requires authentication. Get the contactId from listContacts. Returns name, email, phone, organization, and address.",
            annotations = @Tool.Annotations(
                    title = "Get contact",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ContactDetailResult getContact(
            @ToolArg(description = "Identifier of the contact to fetch, as returned by listContacts.") String contactId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return contactService.getContact(contactId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting contact: %s", e.getMessage());
            return new ContactDetailResult(false, "Failed to get contact: " + e.getMessage());
        }
    }

    @Tool(description = "createContact: Create a new contact for use with domain registrations. Requires authentication. Returns the created contact including its id for assignment to domains.",
            annotations = @Tool.Annotations(
                    title = "Create contact",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ContactResult createContact(
            @ToolArg(description = "Contact's first name.") String firstName,
            @ToolArg(description = "Contact's last name.") String lastName,
            @ToolArg(description = "Contact's email address.") String email,
            @ToolArg(description = "Phone number in '+CC.number' format, e.g. '+1.5551234567'.") String phone,
            @ToolArg(required = false, description = "Organization or company name, if any.") String organization,
            @ToolArg(description = "First street address line.") String street1,
            @ToolArg(required = false, description = "Second street address line, if needed.") String street2,
            @ToolArg(description = "City name.") String city,
            @ToolArg(required = false, description = "State, province, or region, if applicable.") String state,
            @ToolArg(description = "Postal or ZIP code.") String postalCode,
            @ToolArg(description = "Country as a 2-letter ISO 3166-1 alpha-2 code, e.g. 'US'.") String country,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return contactService.createContact(firstName, lastName, email, phone, organization,
                    street1, street2, city, state, postalCode, country);
        } catch (Exception e) {
            Log.errorf(e, "Error creating contact: %s", e.getMessage());
            return new ContactResult(false, "Failed to create contact: " + e.getMessage());
        }
    }

    @Tool(description = "updateContact: Update an existing contact's information. Requires authentication. Only the fields you provide are changed; omitted fields keep their current values. Get the contactId from listContacts. Returns the updated contact.",
            annotations = @Tool.Annotations(
                    title = "Update contact",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ContactResult updateContact(
            @ToolArg(description = "Identifier of the contact to update, as returned by listContacts.") String contactId,
            @ToolArg(required = false, description = "New first name.") String firstName,
            @ToolArg(required = false, description = "New last name.") String lastName,
            @ToolArg(required = false, description = "New email address.") String email,
            @ToolArg(required = false, description = "New phone number in '+CC.number' format, e.g. '+1.5551234567'.") String phone,
            @ToolArg(required = false, description = "New organization or company name.") String organization,
            @ToolArg(required = false, description = "New first street address line.") String street1,
            @ToolArg(required = false, description = "New second street address line.") String street2,
            @ToolArg(required = false, description = "New city name.") String city,
            @ToolArg(required = false, description = "New state, province, or region.") String state,
            @ToolArg(required = false, description = "New postal or ZIP code.") String postalCode,
            @ToolArg(required = false, description = "New country as a 2-letter ISO 3166-1 alpha-2 code, e.g. 'US'.") String country,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return contactService.updateContact(contactId, firstName, lastName, email, phone, organization,
                    street1, street2, city, state, postalCode, country);
        } catch (Exception e) {
            Log.errorf(e, "Error updating contact: %s", e.getMessage());
            return new ContactResult(false, "Failed to update contact: " + e.getMessage());
        }
    }

    @Tool(description = "deleteContact: Stage deletion of a contact. DESTRUCTIVE; fails if the contact is assigned to active domains. Requires authentication. Returns an actionId; present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Delete contact",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult deleteContact(
            @ToolArg(description = "Identifier of the contact to delete, as returned by listContacts.") String contactId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteContact",
                "Permanently delete contact '" + contactId + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> contactService.deleteContact(contactId)
        );
    }

    @Tool(description = "getContactsForDomain: Get all contacts (registrant, admin, tech, billing) assigned to a domain. Requires authentication. Returns the contact assigned to each role.",
            annotations = @Tool.Annotations(
                    title = "Get domain contacts",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainContactsResult getContactsForDomain(
            @ToolArg(description = "Fully qualified domain name whose contacts to fetch, e.g. 'example.com'.") String domain,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return contactService.getContactsForDomain(domain);
        } catch (Exception e) {
            Log.errorf(e, "Error getting domain contacts: %s", e.getMessage());
            return new DomainContactsResult(false, "Failed to get domain contacts: " + e.getMessage());
        }
    }
}
