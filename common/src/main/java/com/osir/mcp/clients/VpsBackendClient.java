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

    // OS install ("build")

    /**
     * Installable templates, keyed by package (before ordering) or by instance (reinstall).
     *
     * Exactly one of packageId/instanceId; the backend 400s on both or neither. They are not
     * interchangeable - the installable set varies with the package spec, so an ARM package offers ARM
     * templates - and there is no unkeyed "all templates" call because VirtFusion does not have one.
     *
     * Ids are per-install and change when templates are re-imported: always resolve at runtime.
     */
    @GET
    @Path("/v1/hosting/vps/os-templates")
    VpsOsTemplateListApiResponse getOsTemplates(
            @QueryParam("packageId") String packageId,
            @QueryParam("instanceId") String instanceId,
            @QueryParam("includeEol") Boolean includeEol,
            @HeaderParam("Authorization") String bearerToken
    );

    /** DESTRUCTIVE on an already-built server: this is how "reinstall" works, and it wipes the disk. */
    @POST
    @Path("/v1/hosting/vps/instances/{instanceId}/build")
    VpsBuildResponse buildVpsInstance(
            @PathParam("instanceId") String instanceId,
            VpsBuildRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    // SSH keys

    /**
     * Idempotent: 201 when newly stored, 200 with the existing key when the account already has this
     * key material. Either way the response carries the id.
     */
    @POST
    @Path("/v1/hosting/vps/ssh-keys")
    jakarta.ws.rs.core.Response storeSshKey(
            VpsSshKeyCreateRequest request,
            @HeaderParam("Authorization") String bearerToken
    );

    @GET
    @Path("/v1/hosting/vps/ssh-keys")
    VpsSshKeyListApiResponse getSshKeys(
            @HeaderParam("Authorization") String bearerToken
    );

    @DELETE
    @Path("/v1/hosting/vps/ssh-keys/{keyId}")
    void deleteSshKey(
            @PathParam("keyId") int keyId,
            @HeaderParam("Authorization") String bearerToken
    );
}
