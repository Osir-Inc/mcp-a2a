package com.osir.a2a.resources;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Singleton;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-user and global rate limiter for A2A endpoints.
 * Limits concurrent requests both globally and per bearer token.
 */
@Provider
@Singleton
public class RateLimitFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(RateLimitFilter.class);
    private static final String ACQUIRED_KEY = "rateLimit.acquired";

    @ConfigProperty(name = "a2a.rate-limit.max-concurrent-global", defaultValue = "50")
    int maxConcurrentGlobal;

    @ConfigProperty(name = "a2a.rate-limit.max-concurrent-per-user", defaultValue = "10")
    int maxConcurrentPerUser;

    private Semaphore globalSemaphore;
    private final Map<String, AtomicInteger> perUserCounts = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        globalSemaphore = new Semaphore(maxConcurrentGlobal);
        LOG.infof("Rate limiter: global=%d, per-user=%d", maxConcurrentGlobal, maxConcurrentPerUser);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        String path = request.getUriInfo().getPath();
        if (!path.startsWith("a2a")) return;

        // Global limit
        if (!globalSemaphore.tryAcquire()) {
            LOG.warnf("Global rate limit exceeded");
            request.abortWith(Response.status(429)
                    .entity("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"Server busy. Please retry.\"}}")
                    .type("application/json")
                    .build());
            return;
        }

        // Per-user limit (by Authorization header fingerprint)
        String userKey = extractUserKey(request);
        AtomicInteger userCount = perUserCounts.computeIfAbsent(userKey, k -> new AtomicInteger(0));
        if (userCount.incrementAndGet() > maxConcurrentPerUser) {
            userCount.decrementAndGet();
            globalSemaphore.release();
            LOG.warnf("Per-user rate limit exceeded for user: %s", userKey);
            request.abortWith(Response.status(429)
                    .entity("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32000,\"message\":\"Too many concurrent requests for this user.\"}}")
                    .type("application/json")
                    .build());
            return;
        }

        request.setProperty(ACQUIRED_KEY, userKey);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String userKey = (String) request.getProperty(ACQUIRED_KEY);
        if (userKey != null) {
            globalSemaphore.release();
            AtomicInteger count = perUserCounts.get(userKey);
            if (count != null) {
                int remaining = count.decrementAndGet();
                if (remaining <= 0) perUserCounts.remove(userKey);
            }
        }
    }

    private String extractUserKey(ContainerRequestContext request) {
        String auth = request.getHeaderString("Authorization");
        if (auth == null || auth.isBlank()) return "anonymous";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(auth.getBytes(StandardCharsets.UTF_8));
            return "user-" + HexFormat.of().formatHex(hash, 0, 8); // first 8 bytes = 16 hex chars
        } catch (Exception e) {
            return "user-" + Integer.toHexString(auth.hashCode());
        }
    }
}
