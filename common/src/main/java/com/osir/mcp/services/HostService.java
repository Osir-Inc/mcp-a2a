package com.osir.mcp.services;

import com.osir.mcp.clients.HostBackendClient;
import com.osir.mcp.models.host.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class HostService {

    private static final Logger LOG = Logger.getLogger(HostService.class);

    @Inject
    @RestClient
    HostBackendClient backendClient;

    @Inject
    AuthService authService;

    public HostCheckResult checkAvailability(String hostname) {
        if (!authService.isAuthenticated()) {
            return new HostCheckResult(false, "Authentication required. Please use loginWithDevice to authenticate.", false, hostname);
        }

        try {
            String token = authService.getCurrentToken();
            HostCreateRequest request = new HostCreateRequest(hostname, null);
            HostCheckResponse response = backendClient.checkHostAvailability(request, token);

            return new HostCheckResult(
                    true,
                    response.getMessage(),
                    response.isAvailable(),
                    response.getHostname()
            );
        } catch (Exception e) {
            LOG.errorf(e, "Error checking host availability for %s: %s", hostname, e.getMessage());
            return new HostCheckResult(false, "Host availability check failed: " + e.getMessage(), false, hostname);
        }
    }

    public HostResult createHost(String hostname, List<String> ipAddresses) {
        if (!authService.isAuthenticated()) {
            return new HostResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            HostCreateRequest request = new HostCreateRequest(hostname, ipAddresses);
            HostRecord record = backendClient.createHost(request, token);

            return new HostResult(true, "Host record created successfully", record);
        } catch (Exception e) {
            LOG.errorf(e, "Error creating host %s: %s", hostname, e.getMessage());
            return new HostResult(false, "Host creation failed: " + e.getMessage());
        }
    }

    public HostListResult getHostsForDomain(String domain) {
        if (!authService.isAuthenticated()) {
            return new HostListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<HostRecord> hosts = backendClient.getHostsForDomain(domain, token);

            HostListResult result = new HostListResult(true, "Host records retrieved successfully");
            result.setHosts(hosts);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing hosts for domain %s: %s", domain, e.getMessage());
            return new HostListResult(false, "Failed to list host records: " + e.getMessage());
        }
    }

    public HostActionResult deleteHost(String hostname) {
        if (!authService.isAuthenticated()) {
            return new HostActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            HostActionResponse response = backendClient.deleteHost(hostname, token);

            return new HostActionResult(response.isSuccess(), response.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting host %s: %s", hostname, e.getMessage());
            return new HostActionResult(false, "Host deletion failed: " + e.getMessage());
        }
    }
}
