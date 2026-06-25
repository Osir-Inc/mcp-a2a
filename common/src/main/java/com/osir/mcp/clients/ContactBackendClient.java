package com.osir.mcp.clients;

import com.osir.mcp.models.contact.*;
import com.osir.mcp.models.contact.ContactListApiResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface ContactBackendClient {

    @GET
    @Path("/v1/contacts")
    ContactListApiResponse listContacts(
            @QueryParam("search") String search,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/contacts/{id}")
    ContactDetail getContact(
            @PathParam("id") String contactId,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/contacts")
    ContactDetail createContact(
            ContactCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @PUT
    @Path("/v1/contacts/{id}")
    ContactDetail updateContact(
            @PathParam("id") String contactId,
            ContactCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @DELETE
    @Path("/v1/contacts/{id}")
    ContactActionResponse deleteContact(
            @PathParam("id") String contactId,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v2/domains/{domain}/contacts")
    DomainContactsResult getContactsForDomain(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );
}
