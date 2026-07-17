package com.osir.mcp.services;

import com.osir.mcp.clients.TransferBackendClient;
import com.osir.mcp.models.transfer.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class TransferService {

    private static final Logger LOG = Logger.getLogger(TransferService.class);

    @Inject
    @RestClient
    TransferBackendClient backendClient;

    @Inject
    AuthService authService;

    public TransferQuoteResult getQuote(String domain) {
        if (!authService.isAuthenticated()) {
            return new TransferQuoteResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransferQuoteResponse response = backendClient.getTransferQuote(domain, token);
            TransferQuoteResult result = new TransferQuoteResult(true, "Transfer quote retrieved successfully");
            result.setDomain(response.getDomain());
            result.setTransferPrice(response.getTransferPrice());
            result.setCurrency(response.getCurrency());
            result.setExtensionYears(response.getExtensionYears());
            result.setNewExpirationDate(response.getNewExpirationDate());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting transfer quote for %s: %s", domain, e.getMessage());
            return new TransferQuoteResult(false, "Failed to get transfer quote: " + e.getMessage());
        }
    }

    public TransferInitiateResult initiateTransfer(String domain, String authCode) {
        if (!authService.isAuthenticated()) {
            return new TransferInitiateResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransferInitiateRequest request = new TransferInitiateRequest(domain, authCode);
            TransferInitiateResponse response = backendClient.initiateTransfer(request, token);
            TransferInitiateResult result = new TransferInitiateResult(response.isSuccess(),
                    response.isSuccess() ? "Transfer initiated successfully" : response.getMessage());
            result.setDomain(response.getDomain());
            result.setTransferId(response.getTransferId());
            result.setStatus(response.getStatus());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error initiating transfer for %s: %s", domain, e.getMessage());
            return new TransferInitiateResult(false, "Failed to initiate transfer: " + e.getMessage());
        }
    }

    public TransferStatusResult getStatus(String domain) {
        if (!authService.isAuthenticated()) {
            return new TransferStatusResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransferStatusResponse response = backendClient.getTransferStatus(domain, token);
            TransferStatusResult result = new TransferStatusResult(true, "Transfer status retrieved successfully");
            result.setDomain(response.getDomain());
            result.setStatus(response.getStatus());
            result.setRequestDate(response.getRequestDate());
            result.setCurrentRegistrar(response.getCurrentRegistrar());
            result.setExpectedCompletion(response.getExpectedCompletion());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting transfer status for %s: %s", domain, e.getMessage());
            return new TransferStatusResult(false, "Failed to get transfer status: " + e.getMessage());
        }
    }

    public TransferActionResult cancelTransfer(String domain) {
        if (!authService.isAuthenticated()) {
            return new TransferActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransferActionResponse response = backendClient.cancelTransfer(domain, token);
            return new TransferActionResult(response.isSuccess(),
                    response.isSuccess() ? "Transfer cancelled successfully" : response.getMessage());
        } catch (Exception e) {
            LOG.errorf(e, "Error cancelling transfer for %s: %s", domain, e.getMessage());
            return new TransferActionResult(false, "Failed to cancel transfer: " + e.getMessage());
        }
    }

    public PendingTransferListResult listPending() {
        if (!authService.isAuthenticated()) {
            return new PendingTransferListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            var response = backendClient.listPendingTransfers(token);
            PendingTransferListResult result = new PendingTransferListResult(true, "Pending transfers retrieved successfully");
            result.setTransfers(response.getTransfers());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing pending transfers: %s", e.getMessage());
            return new PendingTransferListResult(false, "Failed to list pending transfers: " + e.getMessage());
        }
    }
}
