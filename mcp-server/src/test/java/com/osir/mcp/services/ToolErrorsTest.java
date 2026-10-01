package com.osir.mcp.services;

import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolErrorsTest {

    private static WebApplicationException http(int status, String body) {
        return new WebApplicationException(Response.status(status).entity(body).build());
    }

    @Test
    void passesThroughCodeMessageResolution() {
        ToolCallException e = ToolErrors.toolError("Account creation",
                http(403, "{\"code\":\"ACCOUNT_NOT_VERIFIED\",\"message\":\"Account is pending verification\","
                        + "\"resolution\":\"verifyAccount\"}"));
        assertTrue(e.getMessage().contains("ACCOUNT_NOT_VERIFIED"));
        assertTrue(e.getMessage().contains("Account is pending verification"));
        assertTrue(e.getMessage().contains("verifyAccount"));
    }

    @Test
    void handlesEppShape() {
        ToolCallException e = ToolErrors.toolError("Availability check",
                http(400, "{\"errorCode\":\"INVALID_DOMAIN\",\"error\":\"Not a valid domain name\"}"));
        assertTrue(e.getMessage().contains("INVALID_DOMAIN"));
        assertTrue(e.getMessage().contains("Not a valid domain name"));
    }

    @Test
    void nonJsonBody_fallsBackToStatus() {
        ToolCallException e = ToolErrors.toolError("Availability check", http(502, "<html>bad gateway</html>"));
        assertTrue(e.getMessage().contains("502"));
    }

    @Test
    void reportsOurFailuresAndStaysQuietForBusinessRefusals() {
        assertTrue(ToolErrors.worthReporting(500, true), "a 5xx is ours even when it parses");
        assertTrue(ToolErrors.worthReporting(502, false), "bad gateway");
        assertTrue(ToolErrors.worthReporting(400, false), "a body we cannot parse is contract drift");
        assertFalse(ToolErrors.worthReporting(400, true), "INSUFFICIENT_FUNDS is the system working");
        assertFalse(ToolErrors.worthReporting(409, true), "so is DOMAIN_NOT_AVAILABLE");
        assertFalse(ToolErrors.worthReporting(401, false), "an expired session is normal");
        assertFalse(ToolErrors.worthReporting(403, false), "so is a forbidden scope");
        assertFalse(ToolErrors.worthReporting(404, false), "an empty 404 body is ordinary REST, not drift");
        assertFalse(ToolErrors.worthReporting(429, false), "the backend rate-limiting a user is not an incident");
    }

    @Test
    void plainException_usesItsMessage() {
        ToolCallException e = ToolErrors.toolError("Availability check", new RuntimeException("Connection refused"));
        assertTrue(e.getMessage().contains("Connection refused"));
    }
}
