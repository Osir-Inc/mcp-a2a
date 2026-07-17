package com.osir.mcp.services;

import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.clients.TransferBackendClient;
import com.osir.mcp.clients.VpsBackendClient;
import com.osir.mcp.models.account.AccountSummaryResult;
import com.osir.mcp.models.account.UserProfile;
import com.osir.mcp.models.account.UserProfileResult;
import com.osir.mcp.models.billing.BalanceResponse;
import com.osir.mcp.models.transfer.PendingTransfer;
import com.osir.mcp.models.vps.VpsCountResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class AccountService {

    private static final Logger LOG = Logger.getLogger(AccountService.class);

    @Inject
    @RestClient
    DomainBackendClient domainBackendClient;

    @Inject
    @RestClient
    BillingBackendClient billingBackendClient;

    @Inject
    @RestClient
    VpsBackendClient vpsBackendClient;

    @Inject
    @RestClient
    TransferBackendClient transferBackendClient;

    @Inject
    AuthService authService;

    public UserProfileResult getMyProfile() {
        if (!authService.isAuthenticated()) {
            return new UserProfileResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            UserProfile profile = domainBackendClient.getMyProfile(token);
            UserProfileResult result = new UserProfileResult(true, "Profile retrieved successfully");
            result.setCustomerId(profile.getCustomerId());
            result.setName(profile.getName());
            result.setEmail(profile.getEmail());
            result.setOrganization(profile.getOrganization());
            if (profile.getBalance() != null) {
                result.setBalance(profile.getBalance().getAmount() != null
                        ? String.format(java.util.Locale.US, "%.2f", profile.getBalance().getAmount()) : null);
                result.setCurrency(profile.getBalance().getCurrency());
            } else {
                result.setCurrency(profile.getCurrency());
            }
            result.setDomainCount(profile.getDomainCount());
            result.setVpsCount(profile.getVpsCount());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting user profile: %s", e.getMessage());
            return new UserProfileResult(false, "Failed to get user profile: " + e.getMessage());
        }
    }

    public AccountSummaryResult getAccountSummary() {
        if (!authService.isAuthenticated()) {
            return new AccountSummaryResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            AccountSummaryResult result = new AccountSummaryResult(true, "Account summary retrieved successfully");

            // Get user profile
            try {
                UserProfile profile = domainBackendClient.getMyProfile(token);
                result.setCustomerId(profile.getCustomerId());
                result.setName(profile.getName());
                result.setEmail(profile.getEmail());
                result.setOrganization(profile.getOrganization());
                result.setDomainCount(profile.getDomainCount());
            } catch (Exception e) {
                LOG.warnf("Could not fetch profile for account summary: %s", e.getMessage());
            }

            // Get balance
            try {
                BalanceResponse balance = billingBackendClient.getAccountBalance(token);
                result.setBalance(balance.getBalance());
                result.setCurrency(balance.getCurrency());
            } catch (Exception e) {
                LOG.warnf("Could not fetch balance for account summary: %s", e.getMessage());
            }

            // Get VPS count
            try {
                VpsCountResult vpsCount = vpsBackendClient.getVpsInstanceCount(token);
                result.setVpsCount(vpsCount.getCount());
            } catch (Exception e) {
                LOG.warnf("Could not fetch VPS count for account summary: %s", e.getMessage());
            }

            // Get pending transfers
            try {
                var pendingResponse = transferBackendClient.listPendingTransfers(token);
                List<PendingTransfer> pending = pendingResponse.getTransfers() != null
                        ? pendingResponse.getTransfers() : List.of();
                result.setPendingTransferCount(pending.size());
                result.setPendingTransferDomains(pending.stream()
                        .map(PendingTransfer::getDomain)
                        .collect(Collectors.toList()));
            } catch (Exception e) {
                LOG.warnf("Could not fetch pending transfers for account summary: %s", e.getMessage());
            }

            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting account summary: %s", e.getMessage());
            return new AccountSummaryResult(false, "Failed to get account summary: " + e.getMessage());
        }
    }
}
