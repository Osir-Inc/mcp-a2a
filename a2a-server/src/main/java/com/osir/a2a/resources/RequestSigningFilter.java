package com.osir.a2a.resources;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Optional HMAC-SHA256 request signature verification for agent-to-agent calls.
 * When a2a.signing.secret is configured, requests with an X-Signature header are verified.
 * Requests without the header are still allowed (backward compatible).
 * Enable strict mode via a2a.signing.required=true to reject unsigned requests.
 */
@Provider
public class RequestSigningFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(RequestSigningFilter.class);
    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String ALGORITHM = "HmacSHA256";

    @ConfigProperty(name = "a2a.signing.secret")
    Optional<String> signingSecret;

    @ConfigProperty(name = "a2a.signing.required", defaultValue = "false")
    boolean signingRequired;

    @Override
    public void filter(ContainerRequestContext request) {
        if (signingSecret.isEmpty()) return; // Signing not configured

        String path = request.getUriInfo().getPath();
        if (!path.startsWith("a2a")) return;

        String signature = request.getHeaderString(SIGNATURE_HEADER);

        if (signature == null || signature.isBlank()) {
            if (signingRequired) {
                LOG.warnf("Unsigned request rejected (signing required): %s", path);
                request.abortWith(jsonRpcError(401, "Request signature required"));
            }
            return;
        }

        // Verify signature: HMAC-SHA256(path + timestamp, secret)
        String timestamp = request.getHeaderString("X-Timestamp");
        if (timestamp == null) {
            request.abortWith(jsonRpcError(401, "Missing X-Timestamp header"));
            return;
        }

        // Reject requests older than 5 minutes
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - ts) > 300) {
                request.abortWith(jsonRpcError(401, "Request timestamp expired"));
                return;
            }
        } catch (NumberFormatException e) {
            request.abortWith(jsonRpcError(401, "Invalid timestamp"));
            return;
        }

        String payload = path + ":" + timestamp;
        String expected = computeHmac(payload, signingSecret.get());
        if (!signature.equals(expected)) {
            LOG.warnf("Invalid request signature for path: %s", path);
            request.abortWith(jsonRpcError(401, "Invalid request signature"));
        }
    }

    private String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            LOG.errorf("HMAC computation failed: %s", e.getMessage());
            return "";
        }
    }

    private jakarta.ws.rs.core.Response jsonRpcError(int httpStatus, String message) {
        return jakarta.ws.rs.core.Response.status(httpStatus)
                .entity("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"" + message + "\"}}")
                .type("application/json")
                .build();
    }
}
