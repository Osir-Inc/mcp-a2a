package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.telemetry.ErrorReporting;
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
            int status = wae.getResponse().getStatus();
            String body = readBody(wae);
            Map<String, Object> fields = parse(body);
            if (worthReporting(status, fields != null)) {
                ErrorReporting.capture(action, e, status);
            }
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
            return new ToolCallException(action + " failed: backend returned HTTP " + status);
        }
        // A timeout, an unparseable response, a bug in a client: never the user's fault.
        ErrorReporting.capture(action, e, 0);
        return new ToolCallException(action + " failed: " + e.getMessage());
    }

    /**
     * A 4xx carrying a machine-readable code is the system working, DOMAIN_NOT_AVAILABLE and
     * INSUFFICIENT_FUNDS are not incidents. A 5xx is ours, and so is a 4xx whose body we could not
     * parse at all, which is usually contract drift the backend does not know it caused — except for
     * the statuses that routinely come back with an empty body and mean nothing is wrong: an expired
     * session (401), a scope the account does not have (403), a resource that is not there (404),
     * and the backend's own rate limiting (429).
     */
    // ponytail: status alone, because that is all this method can see. A 401 on a session minted
    // seconds ago IS worth a report, but session age lives in SessionAwareAuthService; report it
    // from there if the quiet 401s ever hide something.
    static boolean worthReporting(int status, boolean parsedBody) {
        if (status >= 500) return true;
        if (parsedBody) return false;
        return status != 401 && status != 403 && status != 404 && status != 429;
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
