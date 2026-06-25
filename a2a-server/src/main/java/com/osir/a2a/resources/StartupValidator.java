package com.osir.a2a.resources;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Validates critical external dependencies at startup.
 * Logs clear warnings if backend or KeyCloak is unreachable.
 * Does NOT prevent startup — services may become available later.
 */
@ApplicationScoped
public class StartupValidator {

    private static final Logger LOG = Logger.getLogger(StartupValidator.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @ConfigProperty(name = "quarkus.rest-client.\"domain-backend\".url")
    String backendUrl;

    @ConfigProperty(name = "keycloak.auth-server-url")
    String keycloakUrl;

    void onStart(@Observes StartupEvent event) {
        LOG.info("=== A2A Server Startup Validation ===");
        checkEndpoint("Backend API", backendUrl);
        checkEndpoint("KeyCloak", keycloakUrl);
        LOG.info("=== Startup Validation Complete ===");
    }

    private void checkEndpoint(String name, String url) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            LOG.infof("  %s (%s): reachable (HTTP %d)", name, url, response.statusCode());
        } catch (Exception e) {
            LOG.errorf("  %s (%s): UNREACHABLE — %s. Requests requiring this service will fail.",
                    name, url, e.getMessage());
        }
    }
}
