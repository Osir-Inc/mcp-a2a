package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.*;
import com.osir.mcp.models.contact.RegistrantInfo;
import com.osir.mcp.models.domain.*;
import com.osir.mcp.models.nameserver.NameserverUpdateRequest;
import com.osir.mcp.models.nameserver.NameserverUpdateResponse;
import com.osir.mcp.models.transfer.TransferInitiateResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class DomainService {

    @Inject
    @RestClient
    DomainBackendClient backendClient;

    @Inject
    AuthService authService;

    @Inject
    TransferService transferService;

    // Domain name validation regex
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.[a-zA-Z]{2,}$"
    );

    public DomainAvailabilityResult checkAvailability(String domain) {
        if (!authService.isAuthenticated()) {
            return new DomainAvailabilityResult(domain, false, "Authentication required");
        }

        try {
            String token = authService.getCurrentToken();
            DomainAvailabilityResponse response = backendClient.checkAvailability(domain, token);

            DomainAvailabilityResult result = new DomainAvailabilityResult(
                    domain,
                    response.isAvailable(),
                    response.isAvailable() ? "Domain is available" : response.getReason()
            );

            result.setPrice(response.getPrice());
            result.setCurrency(response.getCurrency());
            result.setPremium(response.isPremium());

            return result;
        } catch (Exception e) {
            return new DomainAvailabilityResult(domain, false, "Error checking availability: " + e.getMessage());
        }
    }

    public BulkAvailabilityResult bulkCheckAvailability(List<String> domains) {
        if (!authService.isAuthenticated()) {
            return new BulkAvailabilityResult(false, "Authentication required");
        }

        try {
            List<DomainAvailabilityResult> results = domains.stream()
                    .map(this::checkAvailability)
                    .collect(Collectors.toList());

            BulkAvailabilityResult result = new BulkAvailabilityResult(true, "Bulk check completed");
            result.setResults(results);
            return result;
        } catch (Exception e) {
            return new BulkAvailabilityResult(false, "Bulk check failed: " + e.getMessage());
        }
    }

    public DomainRegistrationResult registerDomain(String domain, int years, RegistrantInfo registrantInfo,
                                                   List<String> nameservers, boolean privacyProtection, boolean autoRenew) {
        if (!authService.isAuthenticated()) {
            return new DomainRegistrationResult(domain, false, "Authentication required");
        }

        try {
            String token = authService.getCurrentToken();

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
            return new DomainRegistrationResult(domain, false, "Registration failed: " + e.getMessage());
        }
    }

    public DomainTransferResult transferDomain(String domain, String authCode, RegistrantInfo registrantInfo) {
        try {
            TransferInitiateResult transferResult = transferService.initiateTransfer(domain, authCode);

            DomainTransferResult result = new DomainTransferResult(
                    domain,
                    transferResult.isSuccess(),
                    transferResult.getMessage()
            );
            result.setTransactionId(transferResult.getTransferId());
            return result;
        } catch (Exception e) {
            return new DomainTransferResult(domain, false, "Transfer failed: " + e.getMessage());
        }
    }

    public NameserverUpdateResult updateNameservers(String domain, List<String> nameservers) {
        if (!authService.isAuthenticated()) {
            return new NameserverUpdateResult(domain, false, "Authentication required");
        }

        try {
            String token = authService.getCurrentToken();

            NameserverUpdateRequest request = new NameserverUpdateRequest(nameservers, "prod", true);
            NameserverUpdateResponse response = backendClient.updateNameservers(domain, request, token);

            NameserverUpdateResult result = new NameserverUpdateResult(
                    domain,
                    response.isSuccess(),
                    response.isSuccess() ? response.getData().getMessage() : response.getError()
            );

            if (response.isSuccess() && response.getData() != null) {
                result.setUpdatedNameservers(response.getData().getNewNameservers());
            }

            return result;
        } catch (Exception e) {
            return new NameserverUpdateResult(domain, false, "Nameserver update failed: " + e.getMessage());
        }
    }

    public DomainInfoResult getDomainInfo(String domain) {
        if (!authService.isAuthenticated()) {
            return new DomainInfoResult(domain, false, "Authentication required");
        }

        try {
            String token = authService.getCurrentToken();
            // Assuming you have a getDomainInfo endpoint in your backend
            DomainInfoBackendResponse response = backendClient.getDomainInfo(domain, token);
            DomainInfoResult result = new DomainInfoResult(response);
            return result;
        } catch (Exception e) {
            return new DomainInfoResult(domain, false, "Failed to get domain info: " + e.getMessage());
        }
    }

    public UserDomainsResult getUserDomains() {
        if (!authService.isAuthenticated()) {
            return new UserDomainsResult(false, "Authentication required");
        }

        try {
            String bearerToken = authService.getCurrentToken();
            // Assuming you have a getUserDomains endpoint in your backend
             DomainListResponse domains = backendClient.getUserDomains(bearerToken, 0, 100, "desc");

            // For now, return a placeholder implementation
            UserDomainsResult result = new UserDomainsResult(true, "User domains retrieved");
            result.setDomains(domains.getDomains());

            return result;
        } catch (Exception e) {
            return new UserDomainsResult(false, "Failed to list domains: " + e.getMessage());
        }
    }

    public ValidationResult validateDomainName(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            return new ValidationResult(false, "Domain name cannot be empty");
        }

        domain = domain.trim().toLowerCase();

        List<String> issues = new ArrayList<>();

        // Check length
        if (domain.length() > 253) {
            issues.add("Domain name too long (max 253 characters)");
        }

        if (domain.length() < 4) {
            issues.add("Domain name too short (min 4 characters)");
        }

        // Check format
        if (!DOMAIN_PATTERN.matcher(domain).matches()) {
            issues.add("Invalid domain name format");
        }

        // Check for consecutive hyphens
        if (domain.contains("--")) {
            issues.add("Domain name cannot contain consecutive hyphens");
        }

        // Check if starts or ends with hyphen
        if (domain.startsWith("-") || domain.endsWith("-")) {
            issues.add("Domain name cannot start or end with hyphen");
        }

        boolean isValid = issues.isEmpty();
        String message = isValid ? "Domain name is valid" : "Domain name validation failed";

        ValidationResult result = new ValidationResult(isValid, message);
        result.setIssues(issues);

        return result;
    }

    public DomainRenewalResult renewDomain(String domain, int years) {
        if (!authService.isAuthenticated()) {
            return new DomainRenewalResult(false, "Authentication required", domain, null);
        }

        try {
            String token = authService.getCurrentToken();
            DomainRenewalRequest request = new DomainRenewalRequest(domain, years);
            DomainRenewalResponse response = backendClient.renewDomain(domain, request, token);

            return new DomainRenewalResult(
                    response.isSuccess(),
                    response.getMessage(),
                    response.getDomain(),
                    response.getStatus()
            );
        } catch (Exception e) {
            return new DomainRenewalResult(false, "Domain renewal failed: " + e.getMessage(), domain, null);
        }
    }

    public DomainActionResult lockDomain(String domain) {
        if (!authService.isAuthenticated()) {
            return new DomainActionResult(false, "Authentication required", domain, null);
        }

        try {
            String token = authService.getCurrentToken();
            DomainLockResponse response = backendClient.lockDomain(domain, token);

            return new DomainActionResult(
                    true,
                    response.getMessage(),
                    response.getDomain(),
                    response.getStatus()
            );
        } catch (Exception e) {
            return new DomainActionResult(false, "Domain lock failed: " + e.getMessage(), domain, null);
        }
    }

    public DomainActionResult unlockDomain(String domain) {
        if (!authService.isAuthenticated()) {
            return new DomainActionResult(false, "Authentication required", domain, null);
        }

        try {
            String token = authService.getCurrentToken();
            DomainLockResponse response = backendClient.unlockDomain(domain, token);

            return new DomainActionResult(
                    true,
                    response.getMessage(),
                    response.getDomain(),
                    response.getStatus()
            );
        } catch (Exception e) {
            return new DomainActionResult(false, "Domain unlock failed: " + e.getMessage(), domain, null);
        }
    }

    public DomainActionResult updateAutoRenew(String domain, boolean enabled) {
        if (!authService.isAuthenticated()) {
            return new DomainActionResult(false, "Authentication required", domain, null);
        }

        try {
            String token = authService.getCurrentToken();
            AutoRenewResponse response = enabled
                    ? backendClient.enableAutoRenew(domain, token)
                    : backendClient.disableAutoRenew(domain, token);

            return new DomainActionResult(
                    response.isSuccess(),
                    response.getMessage(),
                    response.getDomain(),
                    response.getStatus()
            );
        } catch (Exception e) {
            return new DomainActionResult(false, "Auto-renew update failed: " + e.getMessage(), domain, null);
        }
    }

    public DomainActionResult updatePrivacyProtection(String domain, boolean enabled) {
        if (!authService.isAuthenticated()) {
            return new DomainActionResult(false, "Authentication required", domain, null);
        }

        try {
            String token = authService.getCurrentToken();
            PrivacyResponse response = enabled
                    ? backendClient.enablePrivacy(domain, token)
                    : backendClient.disablePrivacy(domain, token);

            return new DomainActionResult(
                    true,
                    response.getMessage(),
                    response.getDomain(),
                    response.getStatus()
            );
        } catch (Exception e) {
            return new DomainActionResult(false, "Privacy update failed: " + e.getMessage(), domain, null);
        }
    }

    public DomainSuggestionsResult suggestAlternatives(String domain, int limit) {
        try {
            String baseName = domain.contains(".") ? domain.substring(0, domain.lastIndexOf(".")) : domain;

            com.osir.mcp.models.suggestion.DomainSuggestionResponse response =
                    backendClient.suggestDomains(baseName, null, null, null, limit);

            DomainSuggestionsResult result = new DomainSuggestionsResult(true, "Suggestions generated successfully");
            if (response != null && response.getResults() != null) {
                List<DomainSuggestion> suggestions = response.getResults().stream()
                        .map(d -> {
                            DomainSuggestion s = new DomainSuggestion();
                            s.setDomain(d.getName());
                            s.setAvailable("available".equalsIgnoreCase(d.getAvailability()));
                            return s;
                        })
                        .collect(Collectors.toList());
                result.setSuggestions(suggestions);
            }
            return result;
        } catch (Exception e) {
            return new DomainSuggestionsResult(false, "Failed to generate suggestions: " + e.getMessage());
        }
    }
}