package com.osir.mcp.services;

import com.osir.mcp.clients.KeycloakDeviceAuthClient;
import com.osir.mcp.models.auth.AuthTokenResponse;
import com.osir.mcp.models.auth.DeviceCodeResponse;
import com.osir.mcp.models.auth.DeviceLoginStatusResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The conversation session key minted by checkDeviceLoginStatus: it must authenticate
 * subsequent calls independent of the MCP connection id, honor idle/lifetime limits,
 * and die immediately on logout.
 */
class SessionKeyLifecycleTest {

    @Mock
    KeycloakDeviceAuthClient keycloakClient;

    @InjectMocks
    SessionAwareAuthService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.clientId = "osir-cli";
        service.refreshBufferSeconds = 60;
        service.idleTimeoutMinutes = 30;
        service.maxLifetimeHours = 8;
    }

    /** Seeds the PKCE verifier for "dev-code" — checkDeviceLoginStatus refuses codes it never issued. */
    private void startLogin() {
        DeviceCodeResponse dcr = new DeviceCodeResponse();
        dcr.setDeviceCode("dev-code");
        dcr.setUserCode("ABCD-EFGH");
        dcr.setVerificationUri("https://auth.osir.com/device");
        dcr.setExpiresIn(600);
        dcr.setInterval(5);
        when(keycloakClient.requestDeviceCode(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(dcr);
        service.startDeviceLogin("conn-1");
    }

    private DeviceLoginStatusResult login() {
        startLogin();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"preferred_username\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken("header." + payload + ".sig");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("refresh-abc");
        when(keycloakClient.pollDeviceToken(anyString(), anyString(), anyString(), anyString())).thenReturn(tokenResponse);
        return service.checkDeviceLoginStatus("conn-1", "dev-code");
    }

    @Test
    void loginMintsSessionKeyUsableFromAnyConnection() {
        DeviceLoginStatusResult result = login();

        assertTrue(result.isSuccess());
        assertNotNull(result.getSessionKey());
        assertTrue(result.getSessionKey().startsWith(SessionAwareAuthService.SESSION_KEY_PREFIX));
        assertTrue(result.getMessage().contains(result.getSessionKey()),
                "message must tell the model to reuse the key");

        // Token resolvable via the key alone — no connection id involved.
        assertNotNull(service.getCurrentToken(result.getSessionKey()));
        // Distinct logins mint distinct keys.
        assertNotEquals(result.getSessionKey(), login().getSessionKey());
    }

    @Test
    void idleTimeoutKillsSessionAndRevokesTokens() {
        service.idleTimeoutMinutes = -1; // everything is instantly idle-stale, even same-millisecond
        String key = login().getSessionKey();

        assertNull(service.getCurrentToken(key));
        verify(keycloakClient).backchannelLogout("refresh-abc", "osir-cli");
        // Gone for good, not resurrected on retry.
        assertNull(service.getCurrentToken(key));
    }

    @Test
    void maxLifetimeKillsSessionEvenWhenActive() {
        service.maxLifetimeHours = -1; // instantly over the absolute lifetime, even same-millisecond
        String key = login().getSessionKey();

        assertNull(service.getCurrentToken(key));
        verify(keycloakClient).backchannelLogout("refresh-abc", "osir-cli");
    }

    @Test
    void logoutWithSessionKeyRevokesImmediately() {
        String key = login().getSessionKey();
        assertNotNull(service.getCurrentToken(key));

        assertTrue(service.logout(key).isSuccess());

        verify(keycloakClient).revokeToken(anyString(), eq("access_token"), eq("osir-cli"));
        verify(keycloakClient).backchannelLogout("refresh-abc", "osir-cli");
        assertNull(service.getCurrentToken(key));
        // The connection-id twin entry must die with it — no auth via the alias after logout.
        assertNull(service.getCurrentToken("conn-1"));
    }

    @Test
    void loginMessageAdvertisesKeycloakIdleWhenShorter() {
        startLogin();
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"preferred_username\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken("header." + payload + ".sig");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("refresh-abc");
        tokenResponse.setRefreshExpiresIn(300L); // Keycloak SSO idle: 5 min — shorter than our 30
        when(keycloakClient.pollDeviceToken(anyString(), anyString(), anyString(), anyString())).thenReturn(tokenResponse);

        DeviceLoginStatusResult result = service.checkDeviceLoginStatus("conn-1", "dev-code");

        assertTrue(result.getMessage().contains("5 minutes"),
                "message must advertise the tighter Keycloak limit, was: " + result.getMessage());
    }

    @Test
    void unknownOrForgedKeyIsRejected() {
        login();
        assertNull(service.getCurrentToken("osk_forged-nonsense"));
    }

    @Test
    void cleanupRemovesStaleConversationSessions() {
        service.idleTimeoutMinutes = -1;
        String key = login().getSessionKey();

        service.cleanupExpiredSessions();

        assertNull(service.getCurrentToken(key));
        verify(keycloakClient, atLeastOnce()).backchannelLogout("refresh-abc", "osir-cli");
    }
}
