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
    void plainException_usesItsMessage() {
        ToolCallException e = ToolErrors.toolError("Availability check", new RuntimeException("Connection refused"));
        assertTrue(e.getMessage().contains("Connection refused"));
    }
}
