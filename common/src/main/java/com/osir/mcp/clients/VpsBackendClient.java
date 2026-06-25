package com.osir.mcp.clients;

import com.osir.mcp.models.vps.*;
import com.osir.mcp.models.vps.VpsLocationListApiResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface VpsBackendClient {

    // Public catalog endpoints (no auth required)

    @GET
    @Path("/v1/public/catalog/vps")
    VpsPackageListApiResponse getVpsPackages();

    @GET
    @Path("/v1/public/catalog/vps/locations")
    VpsLocationListApiResponse getVpsLocations();

    // Package details (no auth required)

    @GET
    @Path("/v1/hosting/vps/packages/{packageId}")
    VpsPackageSummary getVpsPackageDetails(
            @PathParam("packageId") String packageId
    );

    // Authenticated VPS operations

    @POST
    @Path("/v1/hosting/vps/order")
    VpsOrderResponse orderVps(
            VpsOrderRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/vps/instances")
    List<VpsInstanceSummary> getVpsInstances(
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/vps/instances/{instanceId}")
    VpsInstanceSummary getVpsInstanceDetails(
            @PathParam("instanceId") String instanceId,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/hosting/vps/instances/{instanceId}/delete")
    VpsActionResponse deleteVpsInstance(
            @PathParam("instanceId") String instanceId,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/hosting/vps/instances/{instanceId}/change-payment-term")
    VpsActionResponse changePaymentTerm(
            @PathParam("instanceId") String instanceId,
            VpsPaymentTermRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @POST
    @Path("/v1/hosting/vps/instances/{instanceId}/login")
    VpsPanelLoginResponse loginToVpsPanel(
            @PathParam("instanceId") String instanceId,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/vps/instances/count")
    VpsCountResult getVpsInstanceCount(
            @HeaderParam("Authorization") String bearerToken
    );
}
