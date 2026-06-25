package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.vps.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.VpsService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@ApplicationScoped
public class VpsHostingMCPServer {

    @Inject
    VpsService vpsService;

    @Inject
    PendingActionStore pendingActionStore;

    // Public catalog tools (no auth required)

    @Tool(description = "List available VPS hosting packages with pricing, specs, and locations. No authentication required.")
    public VpsPackageListResult listVpsPackages(McpConnection connection) {
        try {
            return vpsService.listPackages();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS packages: %s", e.getMessage());
            return new VpsPackageListResult(false, "Failed to list VPS packages: " + e.getMessage());
        }
    }

    @Tool(description = "List available VPS hosting locations (cities/countries) with available packages. No authentication required.")
    public VpsLocationListResult listVpsLocations(McpConnection connection) {
        try {
            return vpsService.listLocations();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS locations: %s", e.getMessage());
            return new VpsLocationListResult(false, "Failed to list VPS locations: " + e.getMessage());
        }
    }

    @Tool(description = "Get detailed information about a specific VPS package including all pricing tiers. Required: packageId (string)")
    public VpsPackageDetailResult getVpsPackageDetails(String packageId, McpConnection connection) {
        try {
            return vpsService.getPackageDetails(packageId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS package details: %s", e.getMessage());
            return new VpsPackageDetailResult(false, "Failed to get package details: " + e.getMessage());
        }
    }

    // Authenticated VPS tools

    @RequiresAuth
    @Tool(description = "Stage an order for a new VPS instance. Deducts from account balance. Requires authentication. Required: packageId (VPS package ID), hostname (e.g., 'myserver.example.com'), paymentTerm ('MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', 'TRIENNIAL'). Optional: operatingSystem (OS template to install). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult orderVps(String packageId, String hostname, String paymentTerm, @ToolArg(required = false) String operatingSystem, McpConnection connection) {
        return pendingActionStore.stage(
                "orderVps",
                "Order VPS package '" + packageId + "' for hostname '" + hostname + "' (" + paymentTerm + ") — deducts from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> vpsService.orderVps(packageId, hostname, paymentTerm, operatingSystem)
        );
    }

    @RequiresAuth
    @Tool(description = "List all VPS instances owned by the authenticated user. Requires authentication.")
    public VpsInstanceListResult listMyVpsInstances(McpConnection connection) {
        try {
            return vpsService.listMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS instances: %s", e.getMessage());
            return new VpsInstanceListResult(false, "Failed to list VPS instances: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get detailed information about a specific VPS instance including resource usage. Requires authentication. Required: instanceId (string)")
    public VpsInstanceDetailResult getVpsInstanceDetails(String instanceId, McpConnection connection) {
        try {
            return vpsService.getInstanceDetails(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS instance details: %s", e.getMessage());
            return new VpsInstanceDetailResult(false, "Failed to get instance details: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Stage deletion/cancellation of a VPS instance. DESTRUCTIVE — irreversible. Requires authentication. Required: instanceId (string). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteVpsInstance(String instanceId, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteVpsInstance",
                "Permanently delete/cancel VPS instance '" + instanceId + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> vpsService.deleteInstance(instanceId)
        );
    }

    @RequiresAuth
    @Tool(description = "Change the payment term (billing cycle) for a VPS instance. Requires authentication. Required: instanceId (string), paymentTerm ('MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', 'TRIENNIAL')")
    public VpsActionResult changeVpsPaymentTerm(String instanceId, String paymentTerm, McpConnection connection) {
        try {
            return vpsService.changePaymentTerm(instanceId, paymentTerm);
        } catch (Exception e) {
            Log.errorf(e, "Error changing VPS payment term: %s", e.getMessage());
            return new VpsActionResult(false, "Payment term change failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Generate a one-time login URL to the VPS control panel (VirtFusion) for managing the server. Requires authentication. Required: instanceId (string)")
    public VpsPanelLoginResult loginToVpsPanel(String instanceId, McpConnection connection) {
        try {
            return vpsService.loginToPanel(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS panel login: %s", e.getMessage());
            return new VpsPanelLoginResult(false, "Panel login failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get the total count of VPS instances owned by the authenticated user. Requires authentication.")
    public VpsCountResult countMyVpsInstances(McpConnection connection) {
        try {
            return vpsService.countMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error counting VPS instances: %s", e.getMessage());
            return new VpsCountResult(false, "Failed to count VPS instances: " + e.getMessage());
        }
    }
}
