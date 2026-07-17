package com.osir.mcp.models.vps;

import java.util.List;

public class VpsOrderRequest {
    private String packageId;
    private String hostname;
    private String paymentTerm;
    /**
     * OS to install once the server exists. Resolve the id at runtime from listVpsOsTemplates -
     * VirtFusion's template ids are per-install and change when templates are re-imported, so a
     * hardcoded one will eventually install the wrong OS.
     *
     * Replaces the old free-text `operatingSystem` field, which the backend accepted and silently
     * dropped: every VPS ordered through it came up with no operating system at all.
     */
    private Integer operatingSystemId;
    /** SSH keys to inject during the install. Must belong to the ordering account. */
    private List<Integer> sshKeyIds;

    public VpsOrderRequest() {}

    public VpsOrderRequest(String packageId, String hostname, String paymentTerm,
                           Integer operatingSystemId, List<Integer> sshKeyIds) {
        this.packageId = packageId;
        this.hostname = hostname;
        this.paymentTerm = paymentTerm;
        this.operatingSystemId = operatingSystemId;
        this.sshKeyIds = sshKeyIds;
    }

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public Integer getOperatingSystemId() { return operatingSystemId; }
    public void setOperatingSystemId(Integer operatingSystemId) { this.operatingSystemId = operatingSystemId; }

    public List<Integer> getSshKeyIds() { return sshKeyIds; }
    public void setSshKeyIds(List<Integer> sshKeyIds) { this.sshKeyIds = sshKeyIds; }
}
