package com.osir.mcp.clients;

import com.osir.mcp.models.*;
import com.osir.mcp.models.account.UserProfile;
import com.osir.mcp.models.auth.AuthRequest;
import com.osir.mcp.models.auth.AuthTokenResponse;
import com.osir.mcp.models.domain.*;

import com.osir.mcp.models.nameserver.NameserverUpdateRequest;
import com.osir.mcp.models.nameserver.NameserverUpdateResponse;
import com.osir.mcp.models.suggestion.DomainSuggestionResponse;
import com.osir.mcp.models.suggestion.BulkSuggestionRequest;
import com.osir.mcp.models.suggestion.BulkSuggestionResponse;
import com.osir.mcp.models.suggestion.KeywordAvailabilityResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DomainBackendClient {

    @POST
    @Path("/api/auth")
    AuthTokenResponse authenticate(AuthRequest authRequest);

    // Customer Profile
    @GET
    @Path("/v1/customers/me")
    UserProfile getMyProfile(
            @HeaderParam("Authorization") String bearerToken
    );

    // If you need a refresh token method, it should look like this:
    @POST
    @Path("/api/auth/refresh")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)  // Required for @FormParam
    AuthTokenResponse refreshToken(
            @HeaderParam("Authorization") String bearerToken,
            @FormParam("refresh_token") String refreshToken
    );

    @POST
    @Path("/api/auth/logout")
    void logout(@HeaderParam("Authorization") String bearerToken);

    // Domain Availability Endpoints
    @GET
    @Path("/v2/domains/{domain}/available")
    DomainAvailabilityResponse checkAvailability(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    // Domain Registration Endpoints
    @POST
    @Path("/v2/domains/register")
    DomainRegistrationResponse registerDomain(
            DomainRegistrationRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    // Domain Management Endpoints
    @PUT
    @Path("/v2/domains/{domain}/nameservers")
    NameserverUpdateResponse updateNameservers(
            @PathParam("domain") String domain,
            NameserverUpdateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v2/domains/{domain}/info")
    DomainInfoBackendResponse getDomainInfo(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @PUT
    @Path("/v2/domains/{domain}/contacts")
    ContactUpdateResponse updateContact(
            @PathParam("domain") String domain,
            ContactUpdateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/autorenew/enable")
    AutoRenewResponse enableAutoRenew(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/autorenew/disable")
    AutoRenewResponse disableAutoRenew(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/privacy/enable")
    PrivacyResponse enablePrivacy(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/privacy/disable")
    PrivacyResponse disablePrivacy(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    // User Domain Endpoints
    @GET
    @Path("/v2/domains")
    DomainListResponse getUserDomains(
            @HeaderParam("Authorization") String bearerToken,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("sortDirection") String sortDirection
    );

    // Additional Domain Operations
    @POST
    @Path("/v2/domains/{domain}/lock")
    DomainLockResponse lockDomain(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/unlock")
    DomainLockResponse unlockDomain(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/domains/{domain}/renew")
    DomainRenewalResponse renewDomain(
            @PathParam("domain") String domain,
            DomainRenewalRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    // Domain Name Suggestion Endpoints
    @GET
    @Path("/namesuggestions/suggest")
    DomainSuggestionResponse suggestDomains(
            @QueryParam("name") String name,
            @QueryParam("tlds") String tlds,
            @QueryParam("lang") String lang,
            @QueryParam("use-numbers") Boolean useNumbers,
            @QueryParam("max-results") Integer maxResults
    );

    @GET
    @Path("/namesuggestions/spin-word")
    DomainSuggestionResponse spinWord(
            @QueryParam("name") String name,
            @QueryParam("position") Integer position,
            @QueryParam("similarity") Double similarity,
            @QueryParam("tlds") String tlds,
            @QueryParam("lang") String lang,
            @QueryParam("max-results") Integer maxResults
    );

    @GET
    @Path("/namesuggestions/add-prefix")
    DomainSuggestionResponse addPrefix(
            @QueryParam("name") String name,
            @QueryParam("vocabulary") String vocabulary,
            @QueryParam("tlds") String tlds,
            @QueryParam("lang") String lang,
            @QueryParam("max-results") Integer maxResults
    );

    @GET
    @Path("/namesuggestions/add-suffix")
    DomainSuggestionResponse addSuffix(
            @QueryParam("name") String name,
            @QueryParam("vocabulary") String vocabulary,
            @QueryParam("tlds") String tlds,
            @QueryParam("lang") String lang,
            @QueryParam("max-results") Integer maxResults
    );

    @POST
    @Path("/namesuggestions/bulk-suggest")
    BulkSuggestionResponse bulkSuggest(
            BulkSuggestionRequest request
    );

    @GET
    @Path("/namesuggestions/keyword-availability/{keyword}")
    KeywordAvailabilityResponse checkKeywordAvailability(
            @PathParam("keyword") String keyword,
            @QueryParam("registries") String registries,
            @QueryParam("tlds") String tlds
    );

    @GET
    @Path("/namesuggestions/keyword-availability/{keyword}/summary")
    KeywordAvailabilityResponse checkKeywordAvailabilitySummary(
            @PathParam("keyword") String keyword,
            @QueryParam("registries") String registries,
            @QueryParam("tlds") String tlds
    );
}

