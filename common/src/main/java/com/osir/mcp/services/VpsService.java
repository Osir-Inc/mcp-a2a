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

    /**
     * Order a VPS, optionally installing an OS and injecting SSH keys as part of the same order.
     *
     * operatingSystemId comes from listOsTemplates - it is not free text. Omit it and the server is
     * created without an operating system, which is what happened to every VPS ordered before the
     * backend grew a build step.
     */
    public VpsOrderResult orderVps(String packageId, String hostname, String paymentTerm,
                                   Integer operatingSystemId, List<Integer> sshKeyIds) {
        if (!authService.isAuthenticated()) {
            return new VpsOrderResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsOrderRequest request = new VpsOrderRequest(packageId, hostname, paymentTerm,
                    operatingSystemId, sshKeyIds);
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

    // ---------------------------------------------------------------- OS install

    /**
     * Installable OS templates, keyed by package or by instance.
     *
     * Two keys, because the question has two forms and only one of them has a server to point at:
     *   packageId  - "what could I install if I ordered this?", asked before an order exists. This is
     *                what lets a first order install an OS instead of handing over a bare box.
     *   instanceId - "what can I reinstall this server with?"
     *
     * Not interchangeable: the installable set varies with the package spec (an ARM package offers ARM
     * templates), so answering from the wrong key advertises templates the build then rejects. There is
     * deliberately no unkeyed variant - VirtFusion has no "list all templates" call, and synthesising
     * one from a handy server would produce exactly that mismatch.
     *
     * Ids are per-install: they change when templates are re-imported, so resolve at runtime and never
     * hardcode or remember one.
     */
    public VpsOsTemplateListResult listOsTemplates(String packageId, String instanceId, Boolean includeEol) {
        if (!authService.isAuthenticated()) {
            return new VpsOsTemplateListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }
        // Checked here as well as in the backend so an agent gets a usable instruction rather than a 400.
        if (isBlank(packageId) && isBlank(instanceId)) {
            return new VpsOsTemplateListResult(false,
                    "Either packageId or instanceId is required. Use packageId to see what a package can "
                    + "install before ordering (from listVpsPackages), or instanceId to reinstall an "
                    + "existing server (from listMyVpsInstances).");
        }
        if (!isBlank(packageId) && !isBlank(instanceId)) {
            return new VpsOsTemplateListResult(false,
                    "Pass either packageId or instanceId, not both.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsOsTemplateListApiResponse response = backendClient.getOsTemplates(
                    emptyToNull(packageId), emptyToNull(instanceId), includeEol, token);
            VpsOsTemplateListResult result = new VpsOsTemplateListResult(true, "OS templates retrieved successfully");
            result.setTemplates(response.getTemplates());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing OS templates (packageId=%s, instanceId=%s): %s",
                    packageId, instanceId, e.getMessage());
            return new VpsOsTemplateListResult(false, "Failed to list OS templates: " + e.getMessage());
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** An empty string would be sent as `?packageId=`, which the backend reads as present-but-invalid. */
    private static String emptyToNull(String s) {
        return isBlank(s) ? null : s;
    }

    /**
     * Install an OS on an instance.
     *
     * DESTRUCTIVE on an already-built server - it erases the disk. Gating that is the caller's job.
     * Asynchronous: returns with buildState QUEUED; poll getInstanceDetails until COMPLETE or FAILED.
     */
    public VpsBuildResult buildInstance(String instanceId, Integer operatingSystemId, String hostname,
                                        List<Integer> sshKeyIds, Double swap) {
        if (!authService.isAuthenticated()) {
            return new VpsBuildResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsBuildRequest request = new VpsBuildRequest(operatingSystemId, hostname, sshKeyIds, swap);
            VpsBuildResponse response = backendClient.buildVpsInstance(instanceId, request, token);

            VpsBuildResult result = new VpsBuildResult(true,
                    "OS install queued. Poll getVpsInstanceDetails until buildState is COMPLETE.");
            result.setInstanceId(response.getInstanceId());
            result.setBuildState(response.getBuildState());
            result.setBuilt(response.getBuilt());
            result.setBuildFailed(response.isBuildFailed());
            result.setOsTemplateId(response.getOsTemplateId());
            result.setOsTemplateName(response.getOsTemplateName());
            return result;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            // 409 is an ordinary outcome, not a crash: the backend refuses a second build while one is
            // in flight. Saying "Conflict, status code 409" would read as a bug and invite a retry loop.
            if (e.getResponse() != null && e.getResponse().getStatus() == 409) {
                return new VpsBuildResult(false,
                        "A build is already in progress for this instance. Poll getVpsInstanceDetails until "
                        + "buildState is COMPLETE or FAILED rather than starting another one.");
            }
            // Surface the backend's own reason, not the framework's "Bad Request, status code 400".
            // The reasons here are all actionable and the caller cannot guess them otherwise:
            // "One or more SSH keys do not belong to this account", "VPS instance is not yet
            // provisioned in VirtFusion".
            String detail = readErrorMessage(e.getResponse());
            LOG.errorf(e, "Error building VPS instance %s: %s", instanceId, detail);
            return new VpsBuildResult(false, "OS install failed: " + detail);
        } catch (Exception e) {
            LOG.errorf(e, "Error building VPS instance %s: %s", instanceId, e.getMessage());
            return new VpsBuildResult(false, "OS install failed: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- SSH keys

    /**
     * Store an SSH key, idempotently.
     *
     * The backend answers 201 when it stored a new key and 200 when the account already had this key
     * material, so callers can store-then-use on every run without piling up duplicates.
     */
    public VpsSshKeyResult storeSshKey(String name, String publicKey) {
        if (!authService.isAuthenticated()) {
            return new VpsSshKeyResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            // Returns Response rather than the DTO because 201-vs-200 is what tells us whether the key
            // was newly stored. try-with-resources because a Response holds the connection open.
            try (jakarta.ws.rs.core.Response response =
                         backendClient.storeSshKey(new VpsSshKeyCreateRequest(name, publicKey), token)) {

                // Do NOT assume the REST client threw for us. Whether a Response-returning method still
                // runs the default exception mapper is implementation detail; if it does, the catch below
                // handles it, and if it does not, an error status lands here. Without this check a 400
                // "malformed key" would read as status != 201 and get reported to the user as the
                // cheerful "you already had this key stored".
                if (response.getStatus() >= 400) {
                    String detail = readErrorMessage(response);
                    LOG.errorf("Storing SSH key failed: HTTP %d - %s", response.getStatus(), detail);
                    return new VpsSshKeyResult(false, "Failed to store SSH key: " + detail);
                }

                boolean created = response.getStatus() == 201;
                VpsSshKey key = response.readEntity(VpsSshKey.class);

                VpsSshKeyResult result = new VpsSshKeyResult(true,
                        created ? "SSH key stored." : "You already had this key stored; returning the existing one.");
                result.setKey(key);
                result.setCreated(created);
                return result;
            }
        } catch (jakarta.ws.rs.WebApplicationException e) {
            // This is the path a real 4xx takes: the client's default exception mapper throws before the
            // Response is ever returned, so the status check above only fires if that mapper is disabled.
            // Without this the backend's "Not a valid OpenSSH public key" is replaced by "Bad Request,
            // status code 400" and the user never learns what was wrong with their key.
            String detail = readErrorMessage(e.getResponse());
            LOG.errorf(e, "Error storing SSH key: %s", detail);
            return new VpsSshKeyResult(false, "Failed to store SSH key: " + detail);
        } catch (Exception e) {
            LOG.errorf(e, "Error storing SSH key: %s", e.getMessage());
            return new VpsSshKeyResult(false, "Failed to store SSH key: " + e.getMessage());
        }
    }

    /** Pulls the backend's {"error": "..."} out of a failed response, falling back to the status line. */
    private String readErrorMessage(jakarta.ws.rs.core.Response response) {
        // A WebApplicationException always carries a response in practice, but the fallback below reads
        // getStatus() outside the try - so a null here would throw out of a catch block, replacing a
        // handled error with an NPE the caller never sees coming.
        if (response == null) {
            return "unknown error";
        }
        try {
            String body = response.readEntity(String.class);
            if (body != null && body.contains("\"error\"")) {
                com.fasterxml.jackson.databind.JsonNode node =
                        new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
                if (node.hasNonNull("error")) {
                    return node.get("error").asText();
                }
            }
        } catch (Exception ignored) {
            // Fall through to the generic message below.
        }
        return "HTTP " + response.getStatus();
    }

    public VpsSshKeyListResult listSshKeys() {
        if (!authService.isAuthenticated()) {
            return new VpsSshKeyListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            VpsSshKeyListApiResponse response = backendClient.getSshKeys(token);
            VpsSshKeyListResult result = new VpsSshKeyListResult(true, "SSH keys retrieved successfully");
            result.setKeys(response.getKeys());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing SSH keys: %s", e.getMessage());
            return new VpsSshKeyListResult(false, "Failed to list SSH keys: " + e.getMessage());
        }
    }

    public VpsActionResult deleteSshKey(int keyId) {
        if (!authService.isAuthenticated()) {
            return new VpsActionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            backendClient.deleteSshKey(keyId, token);
            return new VpsActionResult(true, "SSH key " + keyId + " deleted.");
        } catch (Exception e) {
            LOG.errorf(e, "Error deleting SSH key %d: %s", keyId, e.getMessage());
            return new VpsActionResult(false, "Failed to delete SSH key: " + e.getMessage());
        }
    }
}
