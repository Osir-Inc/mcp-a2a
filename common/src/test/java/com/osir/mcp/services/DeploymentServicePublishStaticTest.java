package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.DeployAppBody;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.deploy.DeployDtos.UploadEnvelope;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Exercises the real zip → PUT → deploy path against a stdlib HttpServer standing in for the signed URL. */
class DeploymentServicePublishStaticTest {

    private static final String HTML = "<html><body><h1>Hi</h1></body></html>";

    private final DeploymentService service = new DeploymentService();
    private HttpServer server;
    private final AtomicReference<byte[]> received = new AtomicReference<>();
    private volatile int putStatus = 200;

    @BeforeEach
    void setUp() throws Exception {
        service.client = mock(DeployBackendClient.class);
        service.authService = mock(AuthService.class);
        when(service.authService.getCurrentToken()).thenReturn("Bearer t");
        when(service.authService.parseJwtClaims("t")).thenReturn(Map.of("sub", "u1"));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/put", ex -> {
            received.set(ex.getRequestBody().readAllBytes());
            ex.sendResponseHeaders(putStatus, -1);
            ex.close();
        });
        server.start();
        String putUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/put";
        when(service.client.createUpload("Bearer t", "tenant_u1")).thenReturn(new UploadEnvelope("tk-1", putUrl));
        when(service.client.deploy(any(), eq("Bearer t"), eq("tenant_u1"))).thenReturn(new AppEnvelope(
                new AppDto("app-1", "site", "al", null, null, "node", "DEPLOYING", "https://site.osir.app", null)));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void publishStatic_zipsPutsAndDeploys() throws Exception {
        DeployResult r = service.publishStatic("site", "```html\n" + HTML + "\n```", "al", true);

        assertTrue(r.success(), r.message());
        assertEquals("app-1", r.appId());
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(received.get()))) {
            assertEquals("index.html", zin.getNextEntry().getName());
            String body = new String(zin.readAllBytes());
            assertTrue(body.startsWith("<!doctype html>"));
            assertTrue(body.contains(HTML));
            assertNull(zin.getNextEntry());
        }
        ArgumentCaptor<DeployAppBody> cap = ArgumentCaptor.forClass(DeployAppBody.class);
        verify(service.client).deploy(cap.capture(), any(), any());
        assertEquals("node", cap.getValue().language());
        assertEquals("tk-1", cap.getValue().source().uploadTicket());
    }

    @Test
    void publishStatic_gateFailureSkipsBackend() {
        DeployResult r = service.publishStatic("site", "<div>nope</div>", null, false);

        assertFalse(r.success());
        assertTrue(r.message().contains("complete document"));
        verifyNoInteractions(service.client);
    }

    @Test
    void publishStatic_ownSiteWithCdnScriptsPassesWithoutContract() {
        String ownSite = "<html><h1>a</h1><h1>b</h1><script src=\"https://cdn.example/x.js\"></script></html>";
        assertTrue(service.publishStatic("site", ownSite, null, false).success());
        assertFalse(service.publishStatic("site", ownSite, null, true).success());
    }

    @Test
    void publishStatic_surfacesBackend400Reason() {
        reset(service.client);
        when(service.client.createUpload(any(), any())).thenReturn(new UploadEnvelope("tk-1",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/put"));
        // A built Response can't readEntity(); mock one the way the REST client hands it back.
        var resp = mock(jakarta.ws.rs.core.Response.class);
        when(resp.getStatus()).thenReturn(400);
        when(resp.getStatusInfo()).thenReturn(jakarta.ws.rs.core.Response.Status.BAD_REQUEST);
        when(resp.readEntity(String.class)).thenReturn("{\"error\":\"region must be one of us|al\"}");
        // Construct outside when(): the ctor touches the mocked resp, which would break stubbing.
        var backend400 = new jakarta.ws.rs.WebApplicationException(resp);
        when(service.client.deploy(any(), any(), any())).thenThrow(backend400);

        DeployResult r = service.publishStatic("site", HTML, "zz", true);

        assertFalse(r.success());
        assertTrue(r.message().contains("region must be one of us|al"), r.message());
    }

    @Test
    void publishStatic_uploadFailureDoesNotDeploy() {
        putStatus = 500;
        DeployResult r = service.publishStatic("site", HTML, null, true);

        assertFalse(r.success());
        verify(service.client, never()).deploy(any(), any(), any());
    }
}
