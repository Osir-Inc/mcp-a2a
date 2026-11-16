package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;
import com.osir.mcp.models.deploy.DeployDtos.StatusEnvelope;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** howto_agents.md M4: agents are not apps, and the dashboard-login keys are reserved. */
class DeploymentServiceAgentTest {

    @Mock DeployBackendClient client;
    @Mock AuthService authService;

    @InjectMocks
    DeploymentService service;

    static final AppDto HERMES = new AppDto("app_h1", "hermes-1", "us", "owned", null, "node", "READY",
            "https://agent.example.com", null, "agent", "hermes");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authService.getCurrentToken()).thenReturn("Bearer tok");
        when(authService.parseJwtClaims("tok")).thenReturn(Map.of("sub", "user-1"));
        when(client.status(eq("hermes-1"), anyString(), anyString()))
                .thenReturn(new StatusEnvelope(HERMES, null, null, List.of(), null, "vf-1", "203.0.113.7", null));
    }

    @Test
    void reservedDashboardKeys_areRefusedForEveryApp_withoutTouchingTheBackend() {
        for (String key : List.of("HERMES_DASHBOARD_BASIC_AUTH_PASSWORD", "HERMES_DASHBOARD_BASIC_AUTH_USERNAME",
                "HERMES_DASHBOARD_BASIC_AUTH_SECRET", "hermes_dashboard_public_url")) {
            var r = service.setSecret("my-shop", key, "x");
            assertFalse(r.success(), key);
            assertTrue(r.message().contains("osirAgentResetPassword"), r.message());
        }
        verify(client, never()).setSecret(any(), any(), any(), any());
    }

    @Test
    void everyAppPathRefusesAnAgent_andNamesTheAgentTools() {
        assertRefused(service.setSecret("hermes-1", "OPENAI_API_KEY", "sk").message());
        assertRefused(service.getLogs("hermes-1", 50).message());
        assertRefused(service.provisionDatabase("hermes-1", "postgres").message());
        assertRefused(service.getSource("hermes-1", true).message());
        assertRefused(service.deploy("hermes-1", "node", null, "t-1").message());
        assertRefused(service.deploy("hermes-1", "node", null, null,
                List.of(new com.osir.mcp.models.deploy.DeployDtos.InlineFile("Dockerfile", "FROM x", null))).message());

        verify(client, never()).setSecret(any(), any(), any(), any());
        verify(client, never()).logs(any(), any(), any(), any());
        verify(client, never()).deploy(any(), any(), any());
        verify(client, never()).createInlineUpload(any(), any(), any());
    }

    private static void assertRefused(String message) {
        assertTrue(message.contains("is a Hermes agent, not an app"), message);
        assertTrue(message.contains("osirAgentStatus"), message);
    }

    @Test
    void anAppIsNotRefused() {
        AppDto shop = new AppDto("app_s", "shop", "us", "instant", null, "node", "READY", null, null, "app", null);
        when(client.status(eq("shop"), anyString(), anyString()))
                .thenReturn(new StatusEnvelope(shop, null, null, List.of(), null, null, null, null));
        assertNull(service.refuseIfAgent("shop"));
        assertNull(service.refuseIfAgent("does-not-exist"), "an unreadable app is left to the backend");
    }

    @Test
    void installAgent_503_carriesTheBackendsMessage() {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(503);
        when(response.readEntity(String.class)).thenReturn(
                "{\"error\":{\"code\":\"CONFLICT\",\"message\":\"hermes is not available yet\",\"retryable\":false}}");
        WebApplicationException ex = mock(WebApplicationException.class);
        when(ex.getResponse()).thenReturn(response);
        when(client.installAgent(any(), anyString(), anyString())).thenThrow(ex);

        var call = service.installAgent("hermes-1", "hermes", "vf-1", "203.0.113.7", null, null);

        assertFalse(call.ok());
        assertEquals(503, call.status());
        assertEquals("hermes is not available yet", call.error().message());
    }

    @Test
    void inlineUpload_401_isASessionProblem_notBadFiles() {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(401);
        WebApplicationException ex = mock(WebApplicationException.class);
        when(ex.getResponse()).thenReturn(response);
        when(client.createInlineUpload(any(), anyString(), anyString())).thenThrow(ex);

        var r = service.deploy("shop", "node", null, null,
                List.of(new com.osir.mcp.models.deploy.DeployDtos.InlineFile("index.js", "x", null)));

        assertFalse(r.message().contains("Files rejected"), r.message());
        assertTrue(r.message().contains("Log in again"), r.message());
    }

    @Test
    void boxTooSmall_namesBothSizes_andABiggerVps() {
        String fix = DeploymentService.userFixSteps(new OwnedMoveDto("FAILED", "OWNED_INSTALLING_AGENT", "too small",
                "t", "BOX_TOO_SMALL", false, Map.of("memoryMb", "2048", "requiredMb", "4096")), "");
        assertTrue(fix.contains("2048 MB") && fix.contains("4096 MB"), fix);
        assertTrue(fix.contains("listVpsPackages"));
    }
}
