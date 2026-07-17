package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.clients.KeycloakDeviceAuthClient;
import com.osir.mcp.models.AuthResult;
import com.osir.mcp.models.auth.*;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
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

class AuthServiceDeviceFlowTest {

    @Mock
    DomainBackendClient backendClient;

    @Mock
    KeycloakDeviceAuthClient keycloakClient;

    @InjectMocks
    AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Set config fields via reflection-like approach (Mockito InjectMocks handles @Inject but not @ConfigProperty)
        authService.clientId = "osir-mcp-agent";
        authService.refreshBufferSeconds = 60;
    }

    // ===== startDeviceLogin =====

    @Test
    void startDeviceLogin_success() {
        DeviceCodeResponse response = new DeviceCodeResponse();
        response.setDeviceCode("dev-code-123");
        response.setUserCode("ABCD-EFGH");
        response.setVerificationUri("https://auth.osir.com/device");
        response.setVerificationUriComplete("https://auth.osir.com/device?user_code=ABCD-EFGH");
        response.setExpiresIn(600);
        response.setInterval(5);

        when(keycloakClient.requestDeviceCode("osir-mcp-agent", "openid")).thenReturn(response);

        DeviceLoginResult result = authService.startDeviceLogin();

        assertTrue(result.isSuccess());
        assertEquals("dev-code-123", result.getDeviceCode());
        assertEquals("ABCD-EFGH", result.getUserCode());
        assertEquals("https://auth.osir.com/device", result.getVerificationUri());
        assertEquals("https://auth.osir.com/device?user_code=ABCD-EFGH", result.getVerificationUriComplete());
        assertEquals(600, result.getExpiresIn());
        assertEquals(5, result.getInterval());

        // Verify pending flow tracked
        assertTrue(authService.getPendingDeviceFlows().containsKey("dev-code-123"));
    }

    @Test
    void startDeviceLogin_nullResponse() {
        when(keycloakClient.requestDeviceCode("osir-mcp-agent", "openid")).thenReturn(null);

        DeviceLoginResult result = authService.startDeviceLogin();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No response"));
    }

    @Test
    void startDeviceLogin_exception() {
        when(keycloakClient.requestDeviceCode("osir-mcp-agent", "openid"))
                .thenThrow(new RuntimeException("Connection refused"));

        DeviceLoginResult result = authService.startDeviceLogin();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    // ===== checkDeviceLoginStatus =====

    @Test
    void checkDeviceLoginStatus_pending() {
        // Set up a pending flow
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() + 600_000);

        WebApplicationException wae = mock(WebApplicationException.class);
        Response mockResponse = mock(Response.class);
        when(wae.getResponse()).thenReturn(mockResponse);
        when(mockResponse.readEntity(String.class))
                .thenReturn("{\"error\":\"authorization_pending\",\"error_description\":\"waiting\"}");

        when(keycloakClient.pollDeviceToken(
                "urn:ietf:params:oauth:grant-type:device_code", "osir-mcp-agent", "dev-code-123"))
                .thenThrow(wae);

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertTrue(result.isSuccess());
        assertEquals("pending", result.getStatus());
        // Device code should still be tracked
        assertTrue(authService.getPendingDeviceFlows().containsKey("dev-code-123"));
    }

    @Test
    void checkDeviceLoginStatus_complete() {
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() + 600_000);

        // Create a JWT with preferred_username claim
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"preferred_username\":\"john\"}".getBytes(StandardCharsets.UTF_8));
        String fakeJwt = "header." + payload + ".signature";

        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken(fakeJwt);
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("refresh-abc");

        when(keycloakClient.pollDeviceToken(
                "urn:ietf:params:oauth:grant-type:device_code", "osir-mcp-agent", "dev-code-123"))
                .thenReturn(tokenResponse);

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertTrue(result.isSuccess());
        assertEquals("complete", result.getStatus());
        assertEquals("Bearer", result.getTokenType());
        assertEquals(300L, result.getExpiresIn());

        // Pending flow should be removed
        assertFalse(authService.getPendingDeviceFlows().containsKey("dev-code-123"));

        // Session should be active
        assertTrue(authService.isAuthenticated());
    }

    @Test
    void checkDeviceLoginStatus_expired() {
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() + 600_000);

        WebApplicationException wae = mock(WebApplicationException.class);
        Response mockResponse = mock(Response.class);
        when(wae.getResponse()).thenReturn(mockResponse);
        when(mockResponse.readEntity(String.class))
                .thenReturn("{\"error\":\"expired_token\",\"error_description\":\"code expired\"}");

        when(keycloakClient.pollDeviceToken(
                "urn:ietf:params:oauth:grant-type:device_code", "osir-mcp-agent", "dev-code-123"))
                .thenThrow(wae);

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertFalse(result.isSuccess());
        assertEquals("expired", result.getStatus());
        assertFalse(authService.getPendingDeviceFlows().containsKey("dev-code-123"));
    }

    @Test
    void checkDeviceLoginStatus_denied() {
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() + 600_000);

        WebApplicationException wae = mock(WebApplicationException.class);
        Response mockResponse = mock(Response.class);
        when(wae.getResponse()).thenReturn(mockResponse);
        when(mockResponse.readEntity(String.class))
                .thenReturn("{\"error\":\"access_denied\",\"error_description\":\"denied by user\"}");

        when(keycloakClient.pollDeviceToken(
                "urn:ietf:params:oauth:grant-type:device_code", "osir-mcp-agent", "dev-code-123"))
                .thenThrow(wae);

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertFalse(result.isSuccess());
        assertEquals("denied", result.getStatus());
        assertFalse(authService.getPendingDeviceFlows().containsKey("dev-code-123"));
    }

    @Test
    void checkDeviceLoginStatus_slowDown() {
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() + 600_000);

        WebApplicationException wae = mock(WebApplicationException.class);
        Response mockResponse = mock(Response.class);
        when(wae.getResponse()).thenReturn(mockResponse);
        when(mockResponse.readEntity(String.class))
                .thenReturn("{\"error\":\"slow_down\",\"error_description\":\"too fast\"}");

        when(keycloakClient.pollDeviceToken(
                "urn:ietf:params:oauth:grant-type:device_code", "osir-mcp-agent", "dev-code-123"))
                .thenThrow(wae);

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertTrue(result.isSuccess());
        assertEquals("slow_down", result.getStatus());
    }

    @Test
    void checkDeviceLoginStatus_localExpiry() {
        // Device code expired locally (past the expiresAt time)
        authService.getPendingDeviceFlows().put("dev-code-123",
                System.currentTimeMillis() - 1000); // already expired

        DeviceLoginStatusResult result = authService.checkDeviceLoginStatus("dev-code-123");

        assertFalse(result.isSuccess());
        assertEquals("expired", result.getStatus());
        assertFalse(authService.getPendingDeviceFlows().containsKey("dev-code-123"));
    }

    // ===== refreshSession =====

    @Test
    void refreshSession_success() {
        // Set up an existing session
        String sessionId = "session_test";
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "old-token", "Bearer",
                System.currentTimeMillis() + 30_000, "refresh-token-123", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken("new-token");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("new-refresh-token");

        when(keycloakClient.refreshToken("refresh_token", "refresh-token-123", "osir-mcp-agent"))
                .thenReturn(tokenResponse);

        AuthResult result = authService.refreshSession();

        assertTrue(result.isSuccess());
        assertEquals("Bearer", result.getTokenType());
        assertEquals(300L, result.getExpiresIn());

        // Verify session was updated
        AuthService.SessionInfo updated = authService.getSessions().get(sessionId);
        assertEquals("new-token", updated.getAccessToken());
        assertEquals("new-refresh-token", updated.getRefreshToken());
    }

    @Test
    void refreshSession_noSession() {
        authService.setCurrentSessionId(null);

        AuthResult result = authService.refreshSession();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No active session"));
    }

    @Test
    void refreshSession_noRefreshToken() {
        String sessionId = "session_test";
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "token", "Bearer",
                System.currentTimeMillis() + 30_000, null, "password");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        AuthResult result = authService.refreshSession();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("No refresh token"));
    }

    @Test
    void refreshSession_error() {
        String sessionId = "session_test";
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "token", "Bearer",
                System.currentTimeMillis() + 30_000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        when(keycloakClient.refreshToken("refresh_token", "refresh-token", "osir-mcp-agent"))
                .thenThrow(new RuntimeException("Keycloak down"));

        AuthResult result = authService.refreshSession();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Keycloak down"));
    }

    // ===== getCurrentToken auto-refresh =====

    @Test
    void getCurrentToken_farFromExpiry_noRefresh() {
        String sessionId = "session_test";
        // Token expires in 5 minutes (well beyond 60s buffer)
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "valid-token", "Bearer",
                System.currentTimeMillis() + 300_000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        String token = authService.getCurrentToken();

        assertEquals("Bearer valid-token", token);
        // Should NOT have called refresh
        verifyNoInteractions(keycloakClient);
    }

    @Test
    void getCurrentToken_closeToExpiry_proactiveRefresh() {
        String sessionId = "session_test";
        // Token expires in 30 seconds (within 60s buffer)
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "old-token", "Bearer",
                System.currentTimeMillis() + 30_000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken("refreshed-token");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("new-refresh");

        when(keycloakClient.refreshToken("refresh_token", "refresh-token", "osir-mcp-agent"))
                .thenReturn(tokenResponse);

        String token = authService.getCurrentToken();

        assertEquals("Bearer refreshed-token", token);
    }

    @Test
    void getCurrentToken_expired_refreshSucceeds() {
        String sessionId = "session_test";
        // Token already expired
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "expired-token", "Bearer",
                System.currentTimeMillis() - 1000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        AuthTokenResponse tokenResponse = new AuthTokenResponse();
        tokenResponse.setAccessToken("new-token");
        tokenResponse.setTokenType("Bearer");
        tokenResponse.setExpiresIn(300L);
        tokenResponse.setRefreshToken("new-refresh");

        when(keycloakClient.refreshToken("refresh_token", "refresh-token", "osir-mcp-agent"))
                .thenReturn(tokenResponse);

        String token = authService.getCurrentToken();

        assertEquals("Bearer new-token", token);
    }

    @Test
    void getCurrentToken_expired_refreshFails() {
        String sessionId = "session_test";
        // Token already expired
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "expired-token", "Bearer",
                System.currentTimeMillis() - 1000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        when(keycloakClient.refreshToken("refresh_token", "refresh-token", "osir-mcp-agent"))
                .thenThrow(new RuntimeException("Keycloak down"));

        String token = authService.getCurrentToken();

        assertNull(token);
        assertNull(authService.getCurrentSessionId());
    }

    @Test
    void getCurrentToken_closeToExpiry_refreshFails_returnsCurrentToken() {
        String sessionId = "session_test";
        // Token expires in 30 seconds (within buffer) but refresh fails - should still return valid token
        AuthService.SessionInfo session = new AuthService.SessionInfo(
                "john", "still-valid-token", "Bearer",
                System.currentTimeMillis() + 30_000, "refresh-token", "device");
        authService.getSessions().put(sessionId, session);
        authService.setCurrentSessionId(sessionId);

        when(keycloakClient.refreshToken("refresh_token", "refresh-token", "osir-mcp-agent"))
                .thenThrow(new RuntimeException("Keycloak down"));

        String token = authService.getCurrentToken();

        // Should fall back to the still-valid current token
        assertEquals("Bearer still-valid-token", token);
    }

    // ===== extractUsernameFromToken =====

    @Test
    void extractUsernameFromToken_validJwt() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"preferred_username\":\"alice\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = "header." + payload + ".sig";

        String username = authService.extractUsernameFromToken(jwt);

        assertEquals("alice", username);
    }

    @Test
    void extractUsernameFromToken_noUsername() {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"12345\"}".getBytes(StandardCharsets.UTF_8));
        String jwt = "header." + payload + ".sig";

        String username = authService.extractUsernameFromToken(jwt);

        assertEquals("unknown", username);
    }

    @Test
    void extractUsernameFromToken_invalidJwt() {
        String username = authService.extractUsernameFromToken("not-a-jwt");

        assertEquals("unknown", username);
    }
}
