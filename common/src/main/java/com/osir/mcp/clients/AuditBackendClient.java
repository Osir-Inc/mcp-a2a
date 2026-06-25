package com.osir.mcp.clients;

import com.osir.mcp.models.audit.AuditLogListResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.osir.mcp.models.audit.AuditEntry;
import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface AuditBackendClient {

    @GET
    @Path("/v1/audit/domain/{domain}")
    AuditLogListResponse getDomainAuditTrail(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/audit/customer/{customerId}")
    AuditLogListResponse getCustomerAuditLogs(
            @PathParam("customerId") String customerId,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/audit/recent")
    List<AuditEntry> getRecentActivity(
            @HeaderParam("Authorization") String bearerToken
    );
}
