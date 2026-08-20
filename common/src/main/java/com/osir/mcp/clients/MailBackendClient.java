package com.osir.mcp.clients;

import com.osir.mcp.models.mail.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * Email hosting (Stalwart) endpoints. All require the customer JWT; ownership is enforced
 * server-side. See domain-registrar docs/mail-mcp-tools.md for the contract.
 */
@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface MailBackendClient {

    @GET
    @Path("/v1/hosting/mail/plans")
    List<MailPlan> getPlans(@HeaderParam("Authorization") String bearerToken);

    @GET
    @Path("/v1/hosting/mail/quote")
    MailQuoteResult getQuote(
            @QueryParam("packageId") String packageId,
            @QueryParam("term") String term,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/hosting/mail/domains/{domain}")
    MailDomainEnableResult enableDomain(
            @PathParam("domain") String domain,
            MailEnableDomainRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/mail/domains")
    List<MailDomainInfo> getDomains(@HeaderParam("Authorization") String bearerToken);

    @GET
    @Path("/v1/hosting/mail/domains/{domain}/dns-records")
    List<MailDnsRecordInfo> getDnsRecords(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/hosting/mail/domains/{domain}/dns-verify")
    MailDnsVerifyResult verifyDns(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    /** 201 → {account, password}; the password is returned exactly once. 402 = insufficient balance. */
    @POST
    @Path("/v1/hosting/mail/domains/{domain}/mailboxes")
    MailboxCreateResult createMailbox(
            @PathParam("domain") String domain,
            MailboxCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/mail/mailboxes")
    List<MailboxSummary> getMailboxes(@HeaderParam("Authorization") String bearerToken);

    @PUT
    @Path("/v1/hosting/mail/mailboxes/{id}/password")
    void setPassword(
            @PathParam("id") String mailboxId,
            MailPasswordRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @DELETE
    @Path("/v1/hosting/mail/mailboxes/{id}")
    void deleteMailbox(
            @PathParam("id") String mailboxId,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/mail/usage")
    Map<String, Long> getUsage(@HeaderParam("Authorization") String bearerToken);
}
