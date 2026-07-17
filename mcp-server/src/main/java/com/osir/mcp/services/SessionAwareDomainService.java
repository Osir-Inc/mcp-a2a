package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.*;
import com.osir.mcp.models.domain.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class SessionAwareDomainService {

    private static final Logger LOG = Logger.getLogger(SessionAwareDomainService.class);

    @Inject
    @RestClient
    DomainBackendClient backendClient;

    @Inject
    SessionAwareAuthService sessionAwareAuthService;

    /**
     * Check domain availability using session-based authentication
     */
    public DomainAvailabilityResult checkAvailability(String domain, String chatSessionId) {
        if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
            return new DomainAvailabilityResult(domain, false, "Authentication required");
        }

        try {
            String token = sessionAwareAuthService.getCurrentToken(chatSessionId);
            LOG.infof("Checking domain %s with session token for session %s", domain, chatSessionId);

            DomainAvailabilityResponse response = backendClient.checkAvailability(domain, token);

            DomainAvailabilityResult result = new DomainAvailabilityResult(
                    domain,
                    response.isAvailable(),
                    response.isAvailable() ? "Domain is available" : response.getReason()
            );

            result.setPrice(response.getPrice());
            result.setCurrency(response.getCurrency());
            result.setPremium(response.isPremium());

            LOG.infof("Domain check completed: %s is %s", domain, response.isAvailable() ? "available" : "taken");
            return result;

        } catch (Exception e) {
            LOG.errorf(e, "Error checking domain availability for session %s", chatSessionId);
            return new DomainAvailabilityResult(domain, false, "Error checking availability: " + e.getMessage());
        }
    }

    /**
     * Get domain information using session-based authentication
     */
    public DomainInfoResult getDomainInfo(String domain, String chatSessionId) {
        if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
            return new DomainInfoResult(domain, false, "Authentication required");
        }

        try {
            String token = sessionAwareAuthService.getCurrentToken(chatSessionId);
            DomainInfoBackendResponse response = backendClient.getDomainInfo(domain, token);
            return new DomainInfoResult(response);
        } catch (Exception e) {
            LOG.errorf(e, "Error getting domain info for session %s", chatSessionId);
            return new DomainInfoResult(domain, false, "Failed to get domain info: " + e.getMessage());
        }
    }

    /**
     * List user domains using session-based authentication
     */
    public UserDomainsResult getUserDomains(String chatSessionId) {
        if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
            return new UserDomainsResult(false, "Authentication required");
        }

        try {
            String bearerToken = sessionAwareAuthService.getCurrentToken(chatSessionId);
            DomainListResponse domains = backendClient.getUserDomains(bearerToken, 0, 100, "desc");

            UserDomainsResult result = new UserDomainsResult(true, "User domains retrieved");
            result.setDomains(domains.getDomains());

            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing domains for session %s", chatSessionId);
            return new UserDomainsResult(false, "Failed to list domains: " + e.getMessage());
        }
    }

    /**
     * Bulk check domains using session-based authentication
     */
    public BulkAvailabilityResult bulkCheckAvailability(List<String> domains, String chatSessionId) {
        if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
            return new BulkAvailabilityResult(false, "Authentication required");
        }

        try {
            List<DomainAvailabilityResult> results = domains.stream()
                    .map(domain -> checkAvailability(domain, chatSessionId))
                    .toList();

            BulkAvailabilityResult result = new BulkAvailabilityResult(true, "Bulk check completed");
            result.setResults(results);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error in bulk check for session %s", chatSessionId);
            return new BulkAvailabilityResult(false, "Bulk check failed: " + e.getMessage());
        }
    }

    /**
     * Register domain using session-based authentication
     */
    public DomainRegistrationResult registerDomain(String domain, int years,
                                                   com.osir.mcp.models.contact.RegistrantInfo registrantInfo,
                                                   List<String> nameservers, boolean privacyProtection, boolean autoRenew, String chatSessionId) {

        if (!sessionAwareAuthService.isAuthenticated(chatSessionId)) {
            return new DomainRegistrationResult(domain, false, "Authentication required");
        }

        try {
            String token = sessionAwareAuthService.getCurrentToken(chatSessionId);

            DomainRegistrationRequest request = new DomainRegistrationRequest(domain, years, registrantInfo);
            request.setNameservers(nameservers);
            request.setPrivacyProtection(privacyProtection);
            request.setAutoRenew(autoRenew);

            DomainRegistrationResponse response = backendClient.registerDomain(request, token);

            DomainRegistrationResult result = new DomainRegistrationResult(
                    domain,
                    response.isSuccess(),
                    response.getMessage()
            );

            result.setTransactionId(response.getTransactionId());
            result.setTotalCost(response.getTotalCost());
            result.setExpirationDate(response.getExpirationDate());

            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error registering domain for session %s", chatSessionId);
            return new DomainRegistrationResult(domain, false, "Registration failed: " + e.getMessage());
        }
    }
}