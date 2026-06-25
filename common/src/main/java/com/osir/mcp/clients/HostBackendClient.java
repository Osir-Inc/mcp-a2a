package com.osir.mcp.clients;

import com.osir.mcp.models.host.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface HostBackendClient {

    @POST
    @Path("/v2/hosts/check")
    HostCheckResponse checkHostAvailability(
            HostCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v2/hosts")
    HostRecord createHost(
            HostCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v2/hosts/domain/{domain}")
    List<HostRecord> getHostsForDomain(
            @PathParam("domain") String domain,
            @HeaderParam("Authorization") String bearerToken
    );

    @DELETE
    @Path("/v2/hosts/{hostname}")
    HostActionResponse deleteHost(
            @PathParam("hostname") String hostname,
            @HeaderParam("Authorization") String bearerToken
    );
}
