package com.osir.mcp.services;

import com.osir.mcp.models.AuthStatusResult;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpAuthHelperBearerTest {

    @Mock
    SessionAwareAuthService sessionService;

    @Mock
    CurrentVertxRequest currentVertxRequest;

    @Mock
    RoutingContext routingContext;

    @Mock
    HttpServerRequest httpRequest;

    @InjectMocks
    McpAuthHelper helper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Real AuthService: parseJwtClaims uses only a static ObjectMapper, no injected state needed.
        helper.authService = new AuthService();
        when(currentVertxRequest.getCurrent()).thenReturn(routingContext);
        when(routingContext.request()).thenReturn(httpRequest);
    }

    private static String jwt(String claimsJson) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claimsJson.getBytes(StandardCharsets.UTF_8));
        return "header." + payload + ".sig";
    }

    @Test
    void bearerToken_present() {
        when(httpRequest.getHeader("Authorization")).thenReturn("Bearer abc");
        assertEquals("Bearer abc", helper.bearerToken());
    }

    @Test
    void bearerToken_absentOrNotBearer() {
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        assertNull(helper.bearerToken());
        when(httpRequest.getHeader("Authorization")).thenReturn("Basic dXNlcg==");
        assertNull(helper.bearerToken());
    }

    @Test
    void bearerToken_noActiveRequest() {
        when(currentVertxRequest.getCurrent()).thenReturn(null);
        assertNull(helper.bearerToken());
    }

    @Test
    void bearerAuthStatus_validToken() {
        long exp = System.currentTimeMillis() / 1000 + 3600;
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer " + jwt("{\"preferred_username\":\"alice\",\"exp\":" + exp + "}"));

        AuthStatusResult status = helper.bearerAuthStatus();

        assertNotNull(status);
        assertTrue(status.isAuthenticated());
        assertEquals("alice", status.getUsername());
        assertTrue(status.getTokenExpiresIn() > 3500);
    }

    @Test
    void bearerAuthStatus_expiredToken() {
        long exp = System.currentTimeMillis() / 1000 - 10;
        when(httpRequest.getHeader("Authorization"))
                .thenReturn("Bearer " + jwt("{\"preferred_username\":\"alice\",\"exp\":" + exp + "}"));

        AuthStatusResult status = helper.bearerAuthStatus();

        assertNotNull(status);
        assertFalse(status.isAuthenticated());
    }

    @Test
    void bearerAuthStatus_noBearer_returnsNull() {
        when(httpRequest.getHeader("Authorization")).thenReturn(null);
        assertNull(helper.bearerAuthStatus());
    }
}
