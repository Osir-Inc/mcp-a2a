package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.ws.rs.WebApplicationException;

import java.util.Map;

/**
 * Turns a backend failure into an honest MCP tool error, passing through the backend's
 * machine-readable fields, {@code code}/{@code errorCode}, {@code message}/{@code error} and the
 * new {@code resolution} hint (backend v2.11.0), instead of fabricating a domain-shaped result
 * (audit F1: never report available:false for a non-domain reason).
 */
public final class ToolErrors {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolErrors() {
    }

    /** Wrap any backend/client exception as a ToolCallException the model can act on. */
    public static ToolCallException toolError(String action, Exception e) {
        if (e instanceof WebApplicationException wae) {
            String body = readBody(wae);
            Map<String, Object> fields = parse(body);
            if (fields != null) {
                String code = str(fields, "code", "errorCode");
                String message = str(fields, "message", "error");
                String resolution = str(fields, "resolution");
                StringBuilder sb = new StringBuilder(action).append(" failed");
                if (code != null) sb.append(" [").append(code).append(']');
                sb.append(": ").append(message != null ? message : "HTTP " + wae.getResponse().getStatus());
                if (resolution != null) sb.append(" Resolution: ").append(resolution);
                return new ToolCallException(sb.toString());
            }
            return new ToolCallException(action + " failed: backend returned HTTP "
                    + wae.getResponse().getStatus());
        }
        return new ToolCallException(action + " failed: " + e.getMessage());
    }

    private static String readBody(WebApplicationException e) {
        try {
            return e.getResponse().readEntity(String.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, Object> parse(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.readValue(body, Map.class);
            return map;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String str(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            Object v = map.get(key);
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return null;
    }
}
