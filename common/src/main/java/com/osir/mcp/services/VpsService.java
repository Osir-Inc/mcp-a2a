package com.osir.mcp.services;

import com.osir.mcp.clients.VpsBackendClient;
import com.osir.mcp.models.vps.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class VpsService {

    private static final Logger LOG = Logger.getLogger(VpsService.class);

    @Inject
    @RestClient
    VpsBackendClient backendClient;

    @Inject
    AuthService authService;

    public VpsPackageListResult listPackages() {
        try {
            var response = backendClient.getVpsPackages();
            VpsPackageListResult result = new VpsPackageListResult(true, "VPS packages retrieved successfully");
            result.setPackages(response.getPackages());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing VPS packages: %s", e.getMessage());
            return new VpsPackageListResult(false, "Failed to list VPS packages: " + e.getMessage());
        }
    }

    public VpsLocationListResult listLocations() {
        try {
            var response = backendClient.getVpsLocations();
            VpsLocationListResult result = new VpsLocationListResult(true, "VPS locations retrieved successfully");
            result.setLocations(response.getLocations());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing VPS locations: %s", e.getMessage());
            return new VpsLocationListResult(false, "Failed to list VPS locations: " + e.getMessage());
        }
    }

    public VpsPackageDetailResult getPackageDetails(String packageId) {
        try {
            VpsPackageSummary pkg = backendClient.getVpsPackageDetails(packageId);
            VpsPackageDetailResult result = new VpsPackageDetailResult(true, "Package details retrieved successfully");
            result.setPackageDetail(pkg);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting VPS package details for %s: %s", packageId, e.getMessage());
            return new VpsPackageDetailResult(false, "Failed to get package details: " + e.getMessage());
        }
    }

    public VpsOrderResult orderVps(String packageId, String hostname, String paymentTerm, String operatingSystem) {
        if (!authService.isAuthenticated()) {
            return new VpsOrderResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsOrderRequest request = new VpsOrderRequest(packageId, hostname, paymentTerm, operatingSystem);
            VpsOrderResponse response = backendClient.orderVps(request, token);

            VpsOrderResult result = new VpsOrderResult(response.isSuccess(), response.getMessage());
            result.setInstanceId(response.getInstanceId());
            result.setHostname(response.getHostname());
            result.setPackageName(response.getPackageName());
            result.setLocation(response.getLocation());
            result.setStatus(response.getStatus());
            result.setIpAddress(response.getIpAddress());
            result.setInvoiceNumber(response.getInvoiceNumber());
            result.setOrderId(response.getOrderId());
            result.setTotalAmount(response.getTotalAmount());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error ordering VPS: %s", e.getMessage());
            return new VpsOrderResult(false, "VPS order failed: " + e.getMessage());
        }
    }

    public VpsInstanceListResult listMyInstances() {
        if (!authService.isAuthenticated()) {
            return new VpsInstanceListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<VpsInstanceSummary> instances = backendClient.getVpsInstances(token);
            VpsInstanceListResult result = new VpsInstanceListResult(true, "VPS instances retrieved successfully");
            result.setInstances(instances);
            result.setTotalCount(instances != null ? instances.size() : 0);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing VPS instances: %s", e.getMessage());
            return new VpsInstanceListResult(false, "Failed to list VPS instances: " + e.getMessage());
        }
    }

    public VpsInstanceDetailResult getInstanceDetails(String instanceId) {
        if (!authService.isAuthenticated()) {
            return new VpsInstanceDetailResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsInstanceSummary instance = backendClient.getVpsInstanceDetails(instanceId, token);
            VpsInstanceDetailResult result = new VpsInstanceDetailResult(true, "Instance details retrieved successfully");
            result.setInstance(instance);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting VPS instance details for %s: %s", instanceId, e.getMessage());
            return new VpsInstanceDetailResult(false, "Failed to get instance details: " + e.getMessage());
        }
    }

    public VpsActionResult deleteInstance(String instanceId) {
        if (!authService.isAuthenticated()) {
            return new VpsActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsActionResponse response = backendClient.deleteVpsInstance(instanceId, token);
            VpsActionResult result = new VpsActionResult(response.isSuccess(), response.getMessage());
            result.setInstanceId(response.getInstanceId());
            result.setStatus(response.getStatus());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting VPS instance %s: %s", instanceId, e.getMessage());
            return new VpsActionResult(false, "VPS deletion failed: " + e.getMessage());
        }
    }

    public VpsActionResult changePaymentTerm(String instanceId, String paymentTerm) {
        if (!authService.isAuthenticated()) {
            return new VpsActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsPaymentTermRequest request = new VpsPaymentTermRequest(paymentTerm);
            VpsActionResponse response = backendClient.changePaymentTerm(instanceId, request, token);
            VpsActionResult result = new VpsActionResult(response.isSuccess(), response.getMessage());
            result.setInstanceId(response.getInstanceId());
            result.setStatus(response.getStatus());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error changing payment term for VPS %s: %s", instanceId, e.getMessage());
            return new VpsActionResult(false, "Payment term change failed: " + e.getMessage());
        }
    }

    public VpsPanelLoginResult loginToPanel(String instanceId) {
        if (!authService.isAuthenticated()) {
            return new VpsPanelLoginResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsPanelLoginResponse response = backendClient.loginToVpsPanel(instanceId, token);
            VpsPanelLoginResult result = new VpsPanelLoginResult(response.isSuccess(), response.getMessage());
            result.setLoginUrl(response.getLoginUrl());
            result.setHostname(response.getHostname());
            result.setExpiresAt(response.getExpiresAt());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting VPS panel login for %s: %s", instanceId, e.getMessage());
            return new VpsPanelLoginResult(false, "Panel login failed: " + e.getMessage());
        }
    }

    public VpsCountResult countMyInstances() {
        if (!authService.isAuthenticated()) {
            return new VpsCountResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsCountResult response = backendClient.getVpsInstanceCount(token);
            return new VpsCountResult(true, "VPS instance count retrieved successfully", response.getCount());
        } catch (Exception e) {
            LOG.errorf(e, "Error counting VPS instances: %s", e.getMessage());
            return new VpsCountResult(false, "Failed to count VPS instances: " + e.getMessage());
        }
    }
}
