package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppSourceResult;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.InlineFile;
import com.osir.mcp.models.deploy.DeployDtos.InlineSourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.InlineUploadBody;
import com.osir.mcp.models.deploy.DeployDtos.SourceEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.UploadEnvelope;
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

    // ---- inline deploy (spec_mcp_inline_deploy.md) ----------------------------------------------

    private static final List<InlineFile> FILES = List.of(new InlineFile("index.js", "console.log(1)", null));

    @Test
    void deploy_withFiles_mintsInlineTicketThenDeploysIt() {
        when(client.createInlineUpload(any(), anyString(), anyString())).thenReturn(new UploadEnvelope("t-1", null));
        when(client.deploy(any(), anyString(), anyString())).thenReturn(new AppEnvelope(
                new AppDto("a-1", "my-app", "us", "instant", null, "node", "BUILDING", "https://my-app.us.osir.app", null)));

        DeployResult r = service.deploy("my-app", "node", null, null, FILES);

        assertTrue(r.success(), r.message());
        var upload = org.mockito.ArgumentCaptor.forClass(InlineUploadBody.class);
        verify(client).createInlineUpload(upload.capture(), anyString(), eq("tenant_user-1"));
        assertEquals(FILES, upload.getValue().files());
        var body = org.mockito.ArgumentCaptor.forClass(DeployAppBody.class);
        verify(client).deploy(body.capture(), anyString(), anyString());
        assertEquals("t-1", body.getValue().source().uploadTicket());
        assertTrue(r.message().contains("NOT live yet"));
    }

    @Test
    void deploy_withFilesAndTicket_isRefusedWithoutCallingTheBackend() {
        DeployResult r = service.deploy("my-app", "node", null, "t-x", FILES);

        assertFalse(r.success());
        verifyNoInteractions(client);
    }

    @Test
    void deploy_withFiles_overCapPassesThePlatformsReasonThrough() {
        WebApplicationException tooBig = wae(413);
        when(tooBig.getResponse().readEntity(String.class))
                .thenReturn("{\"error\":{\"code\":\"too_large\",\"message\":\"Source over 6 MiB: drop node_modules\"}}");
        when(client.createInlineUpload(any(), anyString(), anyString())).thenThrow(tooBig);

        DeployResult r = service.deploy("my-app", "node", null, null, FILES);

        assertFalse(r.success());
        assertTrue(r.message().contains("drop node_modules"), r.message());
        verify(client, never()).deploy(any(), anyString(), anyString());
    }

    @Test
    void getSource_inlineReturnsFilesAndTruncatedFlag() {
        when(client.inlineSource(eq("my-app"), eq(true), anyString(), anyString()))
                .thenReturn(new InlineSourceEnvelope(FILES, true, "https://dl/x", "PARTIAL: do NOT redeploy"));

        AppSourceResult r = service.getSource("my-app", true);

        assertTrue(r.success());
        assertEquals(FILES, r.files());
        assertEquals(Boolean.TRUE, r.truncated());
        assertTrue(r.message().contains("PARTIAL"));
        verify(client, never()).source(anyString(), anyString(), anyString());
    }

    @Test
    void getSource_urlOnlyResultOmitsFileFieldsFromJson() throws Exception {
        // A null typed field fails the generated output schema; an omitted one validates.
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(new AppSourceResult(true, "ok", "u", "e", "i"));
        assertFalse(json.contains("files"), json);
        assertFalse(json.contains("truncated"), json);
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
