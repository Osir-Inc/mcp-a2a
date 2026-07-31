package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.DeployDtos.AppSourceResult;
import com.osir.mcp.models.deploy.DeployDtos.SourceEnvelope;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentServiceSourceTest {

    @Mock DeployBackendClient client;
    @Mock AuthService authService;

    @InjectMocks
    DeploymentService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authService.getCurrentToken()).thenReturn("Bearer tok");
        when(authService.parseJwtClaims("tok")).thenReturn(Map.of("sub", "user-1"));
    }

    @Test
    void getSource_returnsSignedUrlWithRedeployInstructions() {
        when(client.source(eq("my-app"), anyString(), anyString()))
                .thenReturn(new SourceEnvelope("https://dl.example/signed", "2026-07-31T12:00:00Z"));

        AppSourceResult result = service.getSource("my-app");

        assertTrue(result.success());
        assertEquals("https://dl.example/signed", result.getUrl());
        assertEquals("2026-07-31T12:00:00Z", result.expiresAt());
        assertTrue(result.instructions().contains("osirAppCreateUpload"));
        assertTrue(result.instructions().contains("osirAppDeploy"));
    }

    /** A WebApplicationException carrying the given status — Response is mocked because plain
     *  unit tests have no JAX-RS RuntimeDelegate to build a real one. */
    private static WebApplicationException wae(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        WebApplicationException ex = mock(WebApplicationException.class);
        when(ex.getResponse()).thenReturn(response);
        return ex;
    }

    @Test
    void getSource_notFoundMapsToNoRetainedSourceMessage() {
        WebApplicationException notFound = wae(404); // built BEFORE when(): wae() stubs its own mocks
        when(client.source(eq("old-app"), anyString(), anyString())).thenThrow(notFound);

        AppSourceResult result = service.getSource("old-app");

        assertFalse(result.success());
        assertTrue(result.message().contains("No retained source"));
        assertTrue(result.message().contains("Redeploying"));
    }

    @Test
    void getSource_otherBackendErrorIsGeneric() {
        WebApplicationException serverError = wae(500);
        when(client.source(eq("my-app"), anyString(), anyString())).thenThrow(serverError);

        AppSourceResult result = service.getSource("my-app");

        assertFalse(result.success());
        assertTrue(result.message().contains("try again"));
        // No backend hostname or raw exception text leaks to the LLM (error policy).
        assertFalse(result.message().contains("500"));
    }
}
