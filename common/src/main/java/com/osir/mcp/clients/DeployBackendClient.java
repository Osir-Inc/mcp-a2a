package com.osir.mcp.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallBody;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.PasswordEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.ProviderBody;
import com.osir.mcp.models.deploy.AgentDtos.ProviderEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ConfirmationEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.InlineSourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.InlineUploadBody;
import com.osir.mcp.models.deploy.DeployDtos.LogsEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.ProvisionDbEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.SecretBody;
import com.osir.mcp.models.deploy.DeployDtos.SourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.StatusEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.UploadEnvelope;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedBody;
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

    /** Same ticket as {@link #createUpload}, but the files travel in the body: for a chat client that
     *  cannot PUT (CONTRACTS §4). Caps 500 files / 6 MiB decoded, 413 above them. */
    @POST
    @Path("/v1/uploads/inline")
    UploadEnvelope createInlineUpload(InlineUploadBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /** The retained source as files (caps 300 files / 2 MiB; over them {@code truncated} is true). */
    @GET
    @Path("/v1/apps/{appId}/source")
    InlineSourceEnvelope inlineSource(@PathParam("appId") String appId, @QueryParam("inline") boolean inline, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /** Signed download URL for the app's current source zip — the zip itself never flows through the MCP. */
    @GET
    @Path("/v1/apps/{appId}/source")
    SourceEnvelope source(@PathParam("appId") String appId, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/apps/{appId}/secrets")
    void setSecret(@PathParam("appId") String appId, SecretBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    @POST
    @Path("/v1/apps/{appId}/database")
    ProvisionDbEnvelope provisionDatabase(@PathParam("appId") String appId, @QueryParam("engine") String engine, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /** Install a vetted agent on a VPS the caller owns (async; status.ownedMove tracks it). 202 carries the
     *  dashboard password ONLY on the call that created the agent. 503 = agent not released yet. */
    @POST
    @Path("/v1/agents")
    AgentInstallEnvelope installAgent(AgentInstallBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /** Connect a model provider: the key becomes a secret, the agent reinstalls on its box. Key never echoed. */
    @POST
    @Path("/v1/agents/{appId}/providers")
    ProviderEnvelope agentProvider(@PathParam("appId") String appId, ProviderBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /** A new dashboard password, returned once; the agent restarts with it. */
    @POST
    @Path("/v1/agents/{appId}/password")
    PasswordEnvelope agentPassword(@PathParam("appId") String appId, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);

    /**
     * Ship the app onto an already-built customer VPS: C2 runs prep + deploy + node registration
     * server-side (the image never touches the client). Idempotent per instanceId — a re-POST
     * re-runs prep (no-op) and deploy (replace). JsonNode because the response contract is not
     * final while the endpoint is being built.
     */
    @POST
    @Path("/v1/apps/{appId}/move-to-owned")
    JsonNode moveToOwned(@PathParam("appId") String appId, MoveToOwnedBody body, @HeaderParam("Authorization") String bearer, @HeaderParam("X-Osir-Tenant") String tenant);
}
