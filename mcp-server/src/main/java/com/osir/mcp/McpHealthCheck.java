package com.osir.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Readiness for the MCP server itself, plus a real reachability probe of the domain backend.
 *
 * <p>The probe is a HEAD to the backend's readiness endpoint: ANY HTTP answer counts as reachable,
 * because this asks "is something listening and routable", not "is the backend healthy" — that is
 * the backend's own /q/health to report.
 *
 * <p>It targets /q/health/ready rather than the base URL because the backend authenticates "/*";
 * probing the root answered 401 on every poll, which is both misleading in its access log and a
 * weaker signal. /q/* is in the backend's public-paths list, so this answers 200.
 *
 * <p>ponytail: a backend blip does NOT take this instance out of rotation. Readiness stays UP and
 * the backend's state is reported as data, because a DOWN here would pull the whole MCP from the
 * load balancer and turn a degraded backend into a total outage — clients get a clear per-tool
 * error instead. Flip to .down() only if the ops story ever wants the LB to shed us.
 */
@Readiness
@ApplicationScoped
public class McpHealthCheck implements HealthCheck {

    /** How long a probe result is reused. Health endpoints are polled far more often than this. */
    private static final long CACHE_MS = 10_000;
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);

    /** Public on the backend (its public-paths list covers /q/*), unlike the authenticated root. */
    private static final String PROBE_PATH = "/q/health/ready";

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @ConfigProperty(name = "quarkus.rest-client.\"domain-backend\".url")
    String backendUrl;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();

    private volatile long probedAt;
    private volatile boolean backendReachable = true;

    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.named("mcp-server")
                .up()
                .withData("version", version)
                .withData("transport", "Streamable HTTP")
                .withData("endpoint", "/mcp")
                .withData("backend", backendReachable() ? "reachable" : "unreachable")
                .build();
    }

    /** Tolerates a configured URL with or without a trailing slash. */
    private URI probeUri() {
        String base = backendUrl.endsWith("/")
                ? backendUrl.substring(0, backendUrl.length() - 1)
                : backendUrl;
        return URI.create(base + PROBE_PATH);
    }

    private boolean backendReachable() {
        long now = System.currentTimeMillis();
        if (now - probedAt < CACHE_MS) {
            return backendReachable;
        }
        boolean reachable;
        try {
            HttpRequest probe = HttpRequest.newBuilder(probeUri())
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .timeout(PROBE_TIMEOUT)
                    .build();
            http.send(probe, HttpResponse.BodyHandlers.discarding());
            reachable = true;                       // it answered; the status code is not our business
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            reachable = false;
        } catch (Exception e) {
            reachable = false;
        }
        backendReachable = reachable;
        probedAt = now;
        return reachable;
    }
}
