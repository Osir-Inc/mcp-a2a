package com.osir.mcp.services;

import com.osir.mcp.clients.DeployBackendClient;
import com.osir.mcp.models.deploy.C2Reason;
import com.osir.mcp.models.deploy.DeployDtos.C2Error;
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

/**
 * Reading C2's error body (CONTRACTS §8, spec_c2_reason_codes.md §2). Until 2026-09-19 the nested
 * {"error": {...}} object was read with asText(), which is "" for an object: every C2 move refusal
 * reached the model as an empty string and no reason could ever match.
 */
class DeploymentServiceC2ErrorTest {

    private static final String MOVE_IN_PROGRESS_BODY = """
            {"error": {"code": "CONFLICT", "reason": "MOVE_IN_PROGRESS",
                       "message": "a move to a different box is already in progress for this app",
                       "retryable": true, "params": {"instanceId": "vps-other"}, "ref": "err_01"}}
            """;

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

    /** Response is mocked: plain unit tests have no JAX-RS RuntimeDelegate to build a real one. */
    private static Response response(int status, String body) {
        Response r = mock(Response.class);
        when(r.getStatus()).thenReturn(status);
        when(r.readEntity(String.class)).thenReturn(body);
        return r;
    }

    private void c2Answers(int status, String body) {
        WebApplicationException ex = mock(WebApplicationException.class);
        Response r = response(status, body);
        when(ex.getResponse()).thenReturn(r);
        when(client.moveToOwned(anyString(), any(), anyString(), anyString())).thenThrow(ex);
    }

    @Test
    void parsesC2sNestedErrorIntoItsParts() {
        C2Error e = DeploymentService.readError(response(409, MOVE_IN_PROGRESS_BODY));

        assertEquals("CONFLICT", e.code());
        assertEquals(C2Reason.MOVE_IN_PROGRESS, e.reason());
        assertEquals("a move to a different box is already in progress for this app", e.message());
        assertEquals(Boolean.TRUE, e.retryable());
        assertEquals("vps-other", e.params().get("instanceId"));
        assertEquals("err_01", e.ref());
    }

    @Test
    void aFlatErrorFromAnotherBackendStillReadsAsItsMessage() {
        C2Error e = DeploymentService.readError(response(404, "{\"error\": \"Zone not found\"}"));

        assertEquals("Zone not found", e.message());
        assertNull(e.reason());
        assertNull(e.retryable());
    }

    @Test
    void aNestedErrorWithoutMessageFallsBackToTheStatusLine() {
        C2Error e = DeploymentService.readError(response(409, "{\"error\": {\"code\": \"CONFLICT\"}}"));

        assertEquals("HTTP 409", e.message());
    }

    @Test
    void moveToOwnedHandsBackC2sReasonForA4xx() {
        c2Answers(409, MOVE_IN_PROGRESS_BODY);

        C2Error e = service.moveToOwned("app1", "vps-1", "1.2.3.4", null);

        assertEquals(C2Reason.MOVE_IN_PROGRESS, e.reason());
        assertFalse(e.message().isBlank());
    }

    @Test
    void moveToOwnedKeepsA5xxGenericAndItsRetryabilityUnknown() {
        c2Answers(500, "{\"error\": {\"code\": \"INTERNAL\", \"message\": \"at be-deploy-7.internal\"}}");

        C2Error e = service.moveToOwned("app1", "vps-1", "1.2.3.4", null);

        assertFalse(e.message().contains("internal"), e.message());
        assertNull(e.reason());
        assertNull(e.retryable());
    }
}
