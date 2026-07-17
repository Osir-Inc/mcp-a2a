package com.osir.mcp.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ConfirmationEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.LogsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.SecretBody;
import com.osir.mcp.models.deploy.DeployDtos.StatusEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.UploadEnvelope;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * Client for the NEW Deploy backend (C2), the deployment-domain counterpart to
 * {@link DomainBackendClient}. Identity is forwarded per request: the KeyCloak bearer plus an
 * {@code X-Osir-Tenant} header derived from the session (never from a tool argument).
 */
@RegisterRestClient(configKey = "deploy-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface DeployBackendClient {

    @GET
    @Path("/v1/apps")
    AppsEnvelope listApps(@HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/apps")
    AppEnvelope deploy(DeployAppBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @GET
    @Path("/v1/apps/{appId}/status")
    StatusEnvelope status(@PathParam("appId") String appId, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @GET
    @Path("/v1/apps/{appId}/logs")
    LogsEnvelope logs(@PathParam("appId") String appId, @QueryParam("tail") Integer tail, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @DELETE
    @Path("/v1/apps/{appId}")
    ConfirmationEnvelope requestDelete(@PathParam("appId") String appId, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/confirmations/{confirmationId}/execute")
    JsonNode execute(@PathParam("confirmationId") String confirmationId, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/uploads")
    UploadEnvelope createUpload(@HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/apps/{appId}/secrets")
    void setSecret(@PathParam("appId") String appId, SecretBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/apps/{appId}/database")
    ProvisionDbEnvelope provisionDatabase(@PathParam("appId") String appId, @QueryParam("engine") String engine, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);
}
