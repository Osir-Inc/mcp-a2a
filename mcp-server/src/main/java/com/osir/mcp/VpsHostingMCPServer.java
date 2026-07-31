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

import java.util.List;

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

    @RequiresAuth
    @Tool(description = "Get detailed information about a specific VPS package including all pricing tiers. Requires authentication. Required: packageId (string). For anonymous browsing use listVpsPackages, which already includes per-term pricing.")
    public VpsPackageDetailResult getVpsPackageDetails(String packageId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.getPackageDetails(packageId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS package details: %s", e.getMessage());
            return new VpsPackageDetailResult(false, "Failed to get package details: " + e.getMessage());
        }
    }

    // Authenticated VPS tools

    @RequiresAuth
    @Tool(description = "Stage an order for a new VPS instance. Deducts from account balance. Requires authentication. Required: packageId (VPS package ID), hostname (e.g., 'myserver.example.com'), paymentTerm ('MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', 'TRIENNIAL'). Optional: operatingSystemId (integer OS template id — resolve one with listVpsOsTemplates using this same packageId; omit to get a server with NO operating system installed), sshKeyIds (integer key ids from listMySshKeys or addSshKey, injected during install — without one you cannot log in). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult orderVps(String packageId, String hostname, String paymentTerm,
            @ToolArg(required = false) Integer operatingSystemId,
            @ToolArg(required = false) List<Integer> sshKeyIds,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        String osPart = operatingSystemId == null
                ? " with NO operating system installed"
                : " with OS template " + operatingSystemId;
        return pendingActionStore.stage(
                "orderVps",
                "Order VPS package '" + packageId + "' for hostname '" + hostname + "' (" + paymentTerm + ")"
                        + osPart + " — deducts from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> vpsService.orderVps(packageId, hostname, paymentTerm, operatingSystemId, sshKeyIds)
        );
    }

    // OS install

    @RequiresAuth
    @Tool(description = "List operating system templates available to install. Requires authentication. "
            + "Pass EXACTLY ONE of: packageId (a VPS package id from listVpsPackages) to see what a package can "
            + "install BEFORE ordering — use this to pick an operatingSystemId for orderVps, so the server arrives "
            + "with an OS already on it; or instanceId (from listMyVpsInstances) to see what an existing server can "
            + "be reinstalled with, for buildVpsInstance. The two are not interchangeable: the available set depends "
            + "on the package, so ask with the key that matches what you are about to do. Optional: includeEol "
            + "(boolean, default false — include end-of-life templates). Template ids are per-install and change "
            + "over time, so always resolve an id here rather than reusing a remembered or hardcoded one.")
    public VpsOsTemplateListResult listVpsOsTemplates(
            @ToolArg(required = false) String packageId,
            @ToolArg(required = false) String instanceId,
            @ToolArg(required = false) Boolean includeEol,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listOsTemplates(packageId, instanceId, includeEol);
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS OS templates: %s", e.getMessage());
            return new VpsOsTemplateListResult(false, "Failed to list OS templates: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Stage an operating system install on a VPS instance. DESTRUCTIVE — ERASES ALL DATA on the server, including any deployed application, and cannot be undone. This is also how a reinstall works. Requires authentication. Required: instanceId (VPS instance ID), operatingSystemId (integer template id from listVpsOsTemplates). Optional: sshKeyIds (integer key ids from listMySshKeys — without one you may not be able to log in), hostname (defaults to the instance's current hostname), swap (256, 512, 768 in MB, or 1, 1.5, 2, 3, 4, 5, 6, 8 in GB). The install is asynchronous — afterwards poll getVpsInstanceDetails until buildState is COMPLETE. Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult buildVpsInstance(String instanceId, Integer operatingSystemId,
            @ToolArg(required = false) List<Integer> sshKeyIds,
            @ToolArg(required = false) String hostname,
            @ToolArg(required = false) Double swap,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return pendingActionStore.stage(
                "buildVpsInstance",
                "Install OS template " + operatingSystemId + " on VPS instance '" + instanceId
                        + "' — ERASES ALL DATA on the server, including any deployed application, and cannot be undone",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> vpsService.buildInstance(instanceId, operatingSystemId, hostname, sshKeyIds, swap)
        );
    }

    // SSH keys

    @RequiresAuth
    @Tool(description = "Store an SSH public key on your account so it can be injected into VPS installs. Idempotent — storing a key you already have returns the existing one instead of creating a duplicate, so it is safe to call before every order. Requires authentication. Required: name (a label, e.g. 'laptop'), publicKey (a single-line OpenSSH public key, e.g. 'ssh-ed25519 AAAA... user@host'). Returns the key id to pass to orderVps or buildVpsInstance.")
    public VpsSshKeyResult addSshKey(String name, String publicKey, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.storeSshKey(name, publicKey);
        } catch (Exception e) {
            Log.errorf(e, "Error storing SSH key: %s", e.getMessage());
            return new VpsSshKeyResult(false, "Failed to store SSH key: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "List the SSH keys stored on your account, with their ids and SHA256 fingerprints. Use this to check whether a key is already stored and to get the ids to pass to orderVps or buildVpsInstance. Requires authentication.")
    public VpsSshKeyListResult listMySshKeys(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listSshKeys();
        } catch (Exception e) {
            Log.errorf(e, "Error listing SSH keys: %s", e.getMessage());
            return new VpsSshKeyListResult(false, "Failed to list SSH keys: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Remove an SSH key from your account. This does not affect servers already built with it, and the key can simply be added again. Requires authentication. Required: keyId (integer key id from listMySshKeys).")
    public VpsActionResult deleteSshKey(int keyId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.deleteSshKey(keyId);
        } catch (Exception e) {
            Log.errorf(e, "Error deleting SSH key: %s", e.getMessage());
            return new VpsActionResult(false, "Failed to delete SSH key: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "List all VPS instances owned by the authenticated user. Requires authentication.")
    public VpsInstanceListResult listMyVpsInstances(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS instances: %s", e.getMessage());
            return new VpsInstanceListResult(false, "Failed to list VPS instances: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get detailed information about a specific VPS instance including resource usage. Requires authentication. Required: instanceId (string)")
    public VpsInstanceDetailResult getVpsInstanceDetails(String instanceId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.getInstanceDetails(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS instance details: %s", e.getMessage());
            return new VpsInstanceDetailResult(false, "Failed to get instance details: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Stage deletion/cancellation of a VPS instance. DESTRUCTIVE — irreversible. Requires authentication. Required: instanceId (string). Returns an actionId — present the summary to the user, then call executeConfirmedAction with the actionId if they approve.")
    public ConfirmationRequiredResult deleteVpsInstance(String instanceId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
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
    public VpsActionResult changeVpsPaymentTerm(String instanceId, String paymentTerm, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.changePaymentTerm(instanceId, paymentTerm);
        } catch (Exception e) {
            Log.errorf(e, "Error changing VPS payment term: %s", e.getMessage());
            return new VpsActionResult(false, "Payment term change failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Generate a one-time login URL to the VPS control panel (VirtFusion) for managing the server. Requires authentication. Required: instanceId (string)")
    public VpsPanelLoginResult loginToVpsPanel(String instanceId, @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.loginToPanel(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS panel login: %s", e.getMessage());
            return new VpsPanelLoginResult(false, "Panel login failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get the total count of VPS instances owned by the authenticated user. Requires authentication.")
    public VpsCountResult countMyVpsInstances(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.countMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error counting VPS instances: %s", e.getMessage());
            return new VpsCountResult(false, "Failed to count VPS instances: " + e.getMessage());
        }
    }
}
