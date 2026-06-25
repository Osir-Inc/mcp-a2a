package com.osir.mcp.clients;

import com.osir.mcp.models.dns.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DnsBackendClient {

    @POST
    @Path("/dns/zones/{domain}/osir-defaults")
    DnsActionResponse createDnsZone(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/dns/domains/{domain}/records")
    List<DnsRecord> listDnsRecords(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/dns/domains/{domain}/records")
    DnsRecord createDnsRecord(
            @PathParam("domain") String domain,
            DnsRecordRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @PUT
    @Path("/dns/domains/{domain}/records/{recordId}")
    DnsRecord updateDnsRecord(
            @PathParam("domain") String domain,
            @PathParam("recordId") String recordId,
            DnsRecordRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @DELETE
    @Path("/dns/domains/{domain}/records/{recordId}")
    void deleteDnsRecord(
            @PathParam("domain") String domain,
            @PathParam("recordId") String recordId,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/dns/domains/{domain}/records/{recordId}")
    DnsRecord getDnsRecord(
            @PathParam("domain") String domain,
            @PathParam("recordId") String recordId,
            @HeaderParam("Authorization") String bearerToken
    );
}
