package com.osir.mcp.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

/**
 * A failed backend call, parsed once.
 *
 * <p>The JAX-RS entity stream is single-shot and three separate things want a piece of it: the
 * human-readable message, the machine-readable {@code error.code}, and the {@code funding} block.
 * Reading it in one place is what makes all three available together, and it means callers surface
 * the backend's own wording - "Insufficient funds. Required: 1499, Available: 300" - instead of the
 * framework's "Payment Required, status code 402".
 *
 * <p>Two error envelopes are in play across this API and both are handled: {@code {"error": "text",
 * "errorCode": "..."}} on the v2 paths, and {@code {"error": {"code", "message", "details"}}} on
 * {@code /v1/payment/*}.
 *
 * <p><b>{@link #hasResponse()} is the one every money path must check.</b> False means no HTTP
 * response came back at all - a read timeout, a reset connection - and therefore that the request
 * may well have reached the backend and been acted on. Treating that as "it failed" is how a card
 * gets charged twice.
 */
public final class BackendError {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int status;
    private final String code;
    private final String message;
    private final String details;
    private final JsonNode funding;

    private BackendError(int status, String code, String message, String details, JsonNode funding) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.details = details;
        this.funding = funding;
    }

    public static BackendError of(Throwable t) {
        Response response = responseOf(t);
        if (response == null) {
            return new BackendError(0, null, rootMessage(t), null, null);
        }

        int status = response.getStatus();
        String body = readBody(response);
        String code = null;
        String message = null;
        String details = null;
        JsonNode funding = null;

        if (body != null && !body.isBlank()) {
            try {
                JsonNode root = MAPPER.readTree(body);
                JsonNode error = root.path("error");
                if (error.isObject()) {
                    code = text(error, "code");
                    message = text(error, "message");
                    if (error.path("details").isObject()) {
                        details = error.get("details").toString();
                    }
                } else if (error.isTextual()) {
                    message = error.asText();
                }
                if (code == null) code = text(root, "errorCode");
                if (message == null) message = text(root, "message");
                if (root.path("funding").isObject()) funding = root.get("funding");
            } catch (Exception ignored) {
                // A body that will not parse is not worth losing the status over.
            }
        }

        return new BackendError(status, code,
                message != null && !message.isBlank() ? message : "HTTP " + status,
                details, funding);
    }

    /** HTTP status, or 0 when the call never got a response. */
    public int status() { return status; }

    /** False for a timeout or transport failure: the request's fate is unknown, not failed. */
    public boolean hasResponse() { return status > 0; }

    public boolean isServerError() { return status >= 500; }

    /** The backend's {@code error.code} / {@code errorCode}, or null if it sent none. */
    public String code() { return code; }

    /** The backend's own wording, falling back to the status line. Never null. */
    public String message() { return message; }

    /** Raw {@code error.details} JSON for the caller to act on, or null. */
    public String details() { return details; }

    /** The insufficient-funds {@code funding} block, or null. */
    public JsonNode funding() { return funding; }

    private static Response responseOf(Throwable t) {
        for (Throwable c = t; c != null && c != c.getCause(); c = c.getCause()) {
            if (c instanceof WebApplicationException wae && wae.getResponse() != null) {
                return wae.getResponse();
            }
        }
        return null;
    }

    private static String readBody(Response response) {
        try {
            if (!response.hasEntity()) {
                return null;
            }
            // Buffer first so anyone else still holding this exception can read it too - the
            // alternative is an empty body at the second reader and a mystifying "HTTP 400".
            response.bufferEntity();
            return response.readEntity(String.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message != null && !message.isBlank() ? message : root.getClass().getSimpleName();
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
