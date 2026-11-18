package com.osir.mcp.services;

import com.osir.mcp.clients.ContactBackendClient;
import com.osir.mcp.models.contact.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class ContactService {

    private static final Logger LOG = Logger.getLogger(ContactService.class);

    @Inject
    @RestClient
    ContactBackendClient backendClient;

    @Inject
    AuthService authService;

    public ContactListResult listContacts(String search) {
        if (!authService.isAuthenticated()) {
            return new ContactListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            var response = backendClient.listContacts(search, token);
            ContactListResult result = new ContactListResult(true, "Contacts retrieved successfully");
            result.setContacts(response.getData());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing contacts: %s", e.getMessage());
            return new ContactListResult(false, "Failed to list contacts: " + e.getMessage());
        }
    }

    public ContactDetailResult getContact(String contactId) {
        if (!authService.isAuthenticated()) {
            return new ContactDetailResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            ContactDetail contact = backendClient.getContact(contactId, token);
            ContactDetailResult result = new ContactDetailResult(true, "Contact retrieved successfully");
            result.setContact(contact);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting contact %s: %s", contactId, e.getMessage());
            return new ContactDetailResult(false, "Failed to get contact: " + e.getMessage());
        }
    }

    /**
     * Why a .al order would fail or land on the wrong owner, or null when it can go ahead. A staged .al order
     * the registry will refuse costs the user a confirmation round for nothing, so callers check before staging.
     */
    public String alRegistrantProblem(Long registrantId, RegistryAttributes override) {
        if (registrantId == null) {
            return ".al domains need registrantId: first create a contact with registryAttributes "
                    + "(entityType, doctype, docnr, nipt unless 'Person Natyror'), then pass the returned id as registrantId.";
        }
        ContactDetailResult contact = getContact(registrantId.toString());
        if (!contact.isSuccess() || contact.getContact() == null) {
            return "Contact " + registrantId + " not found: " + contact.getMessage();
        }
        // The backend REPLACES the stored fields with an override (no merge), so the override must be complete on its own.
        RegistryAttributes attrs = override != null ? override : contact.getContact().getRegistryAttributes();
        List<String> problems = RegistryAttributes.missingForAl(attrs);
        return problems.isEmpty() ? null
                : (override != null ? "registryAttributes on this order" : "Contact " + registrantId)
                  + " is not valid for .al: " + String.join("; ", problems)
                  + ". Create a new contact with complete registryAttributes (contacts cannot be updated with them), "
                  + "or pass all of entityType, doctype, docnr (and nipt) as registryAttributes on this order.";
    }

    public static boolean isAl(String domain) {
        return domain != null && domain.trim().toLowerCase(java.util.Locale.ROOT).endsWith(".al");
    }

    public ContactResult createContact(String firstName, String lastName, String email, String phone,
                                        String organization, String street1, String street2,
                                        String city, String state, String postalCode, String country) {
        return createContact(firstName, lastName, email, phone, organization, street1, street2,
                city, state, postalCode, country, null);
    }

    public ContactResult createContact(String firstName, String lastName, String email, String phone,
                                        String organization, String street1, String street2,
                                        String city, String state, String postalCode, String country,
                                        RegistryAttributes registryAttributes) {
        if (!authService.isAuthenticated()) {
            return new ContactResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            ContactCreateRequest request = new ContactCreateRequest();
            request.setRegistryAttributes(registryAttributes);
            request.setFirstName(firstName);
            request.setLastName(lastName);
            request.setEmail(email);
            request.setPhone(phone);
            request.setOrganization(organization);
            request.setStreet1(street1);
            request.setStreet2(street2);
            request.setCity(city);
            request.setState(state);
            request.setPostalCode(postalCode);
            request.setCountry(country);

            ContactDetail created = backendClient.createContact(request, token);
            ContactResult result = new ContactResult(true, "Contact created successfully");
            result.setContact(created);
            return result;
        } catch (WebApplicationException e) {
            String body = e.getResponse().readEntity(String.class);
            LOG.errorf("Error creating contact: status=%d body=%s", e.getResponse().getStatus(), body);
            return new ContactResult(false, "Failed to create contact: " + body);
        } catch (Exception e) {
            LOG.errorf(e, "Error creating contact: %s", e.getMessage());
            return new ContactResult(false, "Failed to create contact: " + e.getMessage());
        }
    }

    public ContactResult updateContact(String contactId, String firstName, String lastName, String email,
                                        String phone, String organization, String street1, String street2,
                                        String city, String state, String postalCode, String country) {
        if (!authService.isAuthenticated()) {
            return new ContactResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            ContactCreateRequest request = new ContactCreateRequest();
            request.setFirstName(firstName);
            request.setLastName(lastName);
            request.setEmail(email);
            request.setPhone(phone);
            request.setOrganization(organization);
            request.setStreet1(street1);
            request.setStreet2(street2);
            request.setCity(city);
            request.setState(state);
            request.setPostalCode(postalCode);
            request.setCountry(country);

            ContactDetail updated = backendClient.updateContact(contactId, request, token);
            ContactResult result = new ContactResult(true, "Contact updated successfully");
            result.setContact(updated);
            return result;
        } catch (WebApplicationException e) {
            String body = e.getResponse().readEntity(String.class);
            LOG.errorf("Error updating contact %s: status=%d body=%s", contactId, e.getResponse().getStatus(), body);
            return new ContactResult(false, "Failed to update contact: " + body);
        } catch (Exception e) {
            LOG.errorf(e, "Error updating contact %s: %s", contactId, e.getMessage());
            return new ContactResult(false, "Failed to update contact: " + e.getMessage());
        }
    }

    public ContactActionResult deleteContact(String contactId) {
        if (!authService.isAuthenticated()) {
            return new ContactActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            ContactActionResponse response = backendClient.deleteContact(contactId, token);
            return new ContactActionResult(response.isSuccess(),
                    response.isSuccess() ? "Contact deleted successfully" : response.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting contact %s: %s", contactId, e.getMessage());
            return new ContactActionResult(false, "Failed to delete contact: " + e.getMessage());
        }
    }

    public DomainContactsResult getContactsForDomain(String domainId) {
        if (!authService.isAuthenticated()) {
            return new DomainContactsResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            DomainContactsResult response = backendClient.getContactsForDomain(domainId, token);
            response.setSuccess(true);
            response.setMessage("Domain contacts retrieved successfully");
            return response;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting contacts for domain %s: %s", domainId, e.getMessage());
            return new DomainContactsResult(false, "Failed to get domain contacts: " + e.getMessage());
        }
    }
}
