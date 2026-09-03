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

    @Tool(description = "List available VPS hosting packages with pricing, specs, and locations. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "List VPS packages",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsPackageListResult listVpsPackages(McpConnection connection) {
        try {
            return vpsService.listPackages();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS packages: %s", e.getMessage());
            return new VpsPackageListResult(false, "Failed to list VPS packages: " + e.getMessage());
        }
    }

    @Tool(description = "List available VPS hosting locations (cities/countries) with available packages. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "List VPS locations",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsLocationListResult listVpsLocations(McpConnection connection) {
        try {
            return vpsService.listLocations();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS locations: %s", e.getMessage());
            return new VpsLocationListResult(false, "Failed to list VPS locations: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get detailed information about a specific VPS package including all pricing tiers. Requires authentication. For anonymous browsing use listVpsPackages, which already includes per-term pricing.",
            annotations = @Tool.Annotations(
                    title = "Get VPS package details",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsPackageDetailResult getVpsPackageDetails(
            @ToolArg(description = "VPS package id from listVpsPackages.") String packageId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.getPackageDetails(packageId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS package details: %s", e.getMessage());
            return new VpsPackageDetailResult(false, "Failed to get package details: " + e.getMessage());
        }
    }

    // Authenticated VPS tools

    @RequiresAuth
    @Tool(description = "Stage an order for a new VPS instance; deducts from account balance. Requires authentication. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Order a VPS",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult orderVps(
            @ToolArg(description = "VPS package id from listVpsPackages.") String packageId,
            @ToolArg(description = "Hostname for the new server, e.g. 'myserver.example.com'.") String hostname,
            @ToolArg(description = "Billing cycle: 'MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', or 'TRIENNIAL'.") String paymentTerm,
            @ToolArg(required = false, description = "Integer OS template id resolved with listVpsOsTemplates using this same packageId; omit to get a server with NO operating system installed.") Integer operatingSystemId,
            @ToolArg(required = false, description = "Integer SSH key ids from listMySshKeys or addSshKey, injected during install; without one you cannot log in.") List<Integer> sshKeyIds,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        String osPart = operatingSystemId == null
                ? " with NO operating system installed"
                : " with OS template " + operatingSystemId;
        return pendingActionStore.stage(
                "orderVps",
                "Order VPS package '" + packageId + "' for hostname '" + hostname + "' (" + paymentTerm + ")"
                        + osPart + ", deducts from account balance",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.FINANCIAL,
                () -> vpsService.orderVps(packageId, hostname, paymentTerm, operatingSystemId, sshKeyIds)
        );
    }

    // OS install

    @RequiresAuth
    @Tool(description = "List operating system templates available to install. Requires authentication. "
            + "Pass EXACTLY ONE of packageId (to pick an operatingSystemId for orderVps, so the server arrives "
            + "with an OS on it) or instanceId (to pick a template for reinstalling via buildVpsInstance). The two "
            + "are not interchangeable: the available set depends on the package. Template ids change over time, "
            + "so always resolve an id here rather than reusing a remembered or hardcoded one.",
            annotations = @Tool.Annotations(
                    title = "List VPS OS templates",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsOsTemplateListResult listVpsOsTemplates(
            @ToolArg(required = false, description = "VPS package id from listVpsPackages; use BEFORE ordering to pick an operatingSystemId for orderVps.") String packageId,
            @ToolArg(required = false, description = "VPS instance id from listMyVpsInstances; use to see what an existing server can be reinstalled with via buildVpsInstance.") String instanceId,
            @ToolArg(required = false, description = "Include end-of-life templates (default false).") Boolean includeEol,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listOsTemplates(packageId, instanceId, includeEol);
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS OS templates: %s", e.getMessage());
            return new VpsOsTemplateListResult(false, "Failed to list OS templates: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Stage an operating system install (or reinstall) on a VPS instance. DESTRUCTIVE: ERASES ALL DATA on the server, including any deployed application, and cannot be undone. The install is asynchronous; afterwards poll getVpsInstanceDetails until buildState is COMPLETE. Requires authentication. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Install OS on VPS",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = false,
                    openWorldHint = false))
    public ConfirmationRequiredResult buildVpsInstance(
            @ToolArg(description = "VPS instance id from listMyVpsInstances.") String instanceId,
            @ToolArg(description = "Integer OS template id from listVpsOsTemplates, resolved with this same instanceId.") Integer operatingSystemId,
            @ToolArg(required = false, description = "Integer SSH key ids from listMySshKeys, injected during install; without one you may not be able to log in.") List<Integer> sshKeyIds,
            @ToolArg(required = false, description = "Hostname for the rebuilt server; defaults to the instance's current hostname.") String hostname,
            @ToolArg(required = false, description = "Swap size: 256, 512, or 768 (MB), or 1, 1.5, 2, 3, 4, 5, 6, or 8 (GB).") Double swap,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
            McpConnection connection) {
        return pendingActionStore.stage(
                "buildVpsInstance",
                "Install OS template " + operatingSystemId + " on VPS instance '" + instanceId
                        + "', ERASES ALL DATA on the server, including any deployed application, and cannot be undone",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> vpsService.buildInstance(instanceId, operatingSystemId, hostname, sshKeyIds, swap)
        );
    }

    // SSH keys

    @RequiresAuth
    @Tool(description = "Store an SSH public key on your account so it can be injected into VPS installs. Idempotent: storing a key you already have returns the existing one instead of creating a duplicate, so it is safe to call before every order. Returns the key id to pass to orderVps or buildVpsInstance. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Add SSH key",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsSshKeyResult addSshKey(
            @ToolArg(description = "A label for the key, e.g. 'laptop'.") String name,
            @ToolArg(description = "The full single-line OpenSSH public key, e.g. 'ssh-ed25519 AAAA... user@host'.") String publicKey,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.storeSshKey(name, publicKey);
        } catch (Exception e) {
            Log.errorf(e, "Error storing SSH key: %s", e.getMessage());
            return new VpsSshKeyResult(false, "Failed to store SSH key: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "List the SSH keys stored on your account, with their ids and SHA256 fingerprints. Use this to check whether a key is already stored and to get the ids to pass to orderVps or buildVpsInstance. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List SSH keys",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsSshKeyListResult listMySshKeys(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listSshKeys();
        } catch (Exception e) {
            Log.errorf(e, "Error listing SSH keys: %s", e.getMessage());
            return new VpsSshKeyListResult(false, "Failed to list SSH keys: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Remove an SSH key from your account. This does not affect servers already built with it, and the key can simply be added again. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Delete SSH key",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsActionResult deleteSshKey(
            @ToolArg(description = "Integer key id from listMySshKeys.") int keyId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.deleteSshKey(keyId);
        } catch (Exception e) {
            Log.errorf(e, "Error deleting SSH key: %s", e.getMessage());
            return new VpsActionResult(false, "Failed to delete SSH key: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "List all VPS instances owned by the authenticated user. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "List my VPS instances",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsInstanceListResult listMyVpsInstances(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.listMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error listing VPS instances: %s", e.getMessage());
            return new VpsInstanceListResult(false, "Failed to list VPS instances: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get detailed information about a specific VPS instance including resource usage. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Get VPS instance details",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsInstanceDetailResult getVpsInstanceDetails(
            @ToolArg(description = "VPS instance id from listMyVpsInstances.") String instanceId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.getInstanceDetails(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS instance details: %s", e.getMessage());
            return new VpsInstanceDetailResult(false, "Failed to get instance details: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Stage deletion/cancellation of a VPS instance. DESTRUCTIVE and irreversible. Requires authentication. Returns an actionId: present the summary to the user, then call executeConfirmedAction with the actionId if they approve.",
            annotations = @Tool.Annotations(
                    title = "Delete VPS instance",
                    readOnlyHint = false,
                    destructiveHint = true,
                    idempotentHint = true,
                    openWorldHint = false))
    public ConfirmationRequiredResult deleteVpsInstance(
            @ToolArg(description = "VPS instance id from listMyVpsInstances.") String instanceId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        return pendingActionStore.stage(
                "deleteVpsInstance",
                "Permanently delete/cancel VPS instance '" + instanceId + "'",
                connection.id(),
                DestructiveOpRateLimiter.Bucket.DESTRUCTIVE,
                () -> vpsService.deleteInstance(instanceId)
        );
    }

    @RequiresAuth
    @Tool(description = "Change the payment term (billing cycle) for a VPS instance. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Change VPS payment term",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public VpsActionResult changeVpsPaymentTerm(
            @ToolArg(description = "VPS instance id from listMyVpsInstances.") String instanceId,
            @ToolArg(description = "New billing cycle: 'MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', or 'TRIENNIAL'.") String paymentTerm,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.changePaymentTerm(instanceId, paymentTerm);
        } catch (Exception e) {
            Log.errorf(e, "Error changing VPS payment term: %s", e.getMessage());
            return new VpsActionResult(false, "Payment term change failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Generate a one-time login URL to the VPS control panel (VirtFusion) for managing the server. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Log in to VPS panel",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public VpsPanelLoginResult loginToVpsPanel(
            @ToolArg(description = "VPS instance id from listMyVpsInstances.") String instanceId,
            @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.loginToPanel(instanceId);
        } catch (Exception e) {
            Log.errorf(e, "Error getting VPS panel login: %s", e.getMessage());
            return new VpsPanelLoginResult(false, "Panel login failed: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(description = "Get the total count of VPS instances owned by the authenticated user. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Count my VPS instances",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VpsCountResult countMyVpsInstances(@ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey, McpConnection connection) {
        try {
            return vpsService.countMyInstances();
        } catch (Exception e) {
            Log.errorf(e, "Error counting VPS instances: %s", e.getMessage());
            return new VpsCountResult(false, "Failed to count VPS instances: " + e.getMessage());
        }
    }
}
