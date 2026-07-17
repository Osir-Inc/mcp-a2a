package com.osir.mcp.clients;

import com.osir.mcp.models.auth.AuthTokenResponse;
import com.osir.mcp.models.auth.DeviceCodeResponse;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "keycloak-auth")
@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
@Produces(MediaType.APPLICATION_JSON)
public interface KeycloakDeviceAuthClient {

    @POST
    @Path("/protocol/openid-connect/auth/device")
    DeviceCodeResponse requestDeviceCode(
            @FormParam("client_id") String clientId,
            @FormParam("scope") String scope
    );

    @POST
    @Path("/protocol/openid-connect/token")
    AuthTokenResponse pollDeviceToken(
            @FormParam("grant_type") String grantType,
            @FormParam("client_id") String clientId,
            @FormParam("device_code") String deviceCode
    );

    @POST
    @Path("/protocol/openid-connect/token")
    AuthTokenResponse refreshToken(
            @FormParam("grant_type") String grantType,
            @FormParam("refresh_token") String refreshToken,
            @FormParam("client_id") String clientId
    );

    @POST
    @Path("/protocol/openid-connect/revoke")
    @Produces(MediaType.TEXT_PLAIN)
    void revokeToken(
            @FormParam("token") String token,
            @FormParam("token_type_hint") String tokenTypeHint,
            @FormParam("client_id") String clientId
    );

    @POST
    @Path("/protocol/openid-connect/logout")
    @Produces(MediaType.TEXT_PLAIN)
    void backchannelLogout(
            @FormParam("refresh_token") String refreshToken,
            @FormParam("client_id") String clientId
    );
}
