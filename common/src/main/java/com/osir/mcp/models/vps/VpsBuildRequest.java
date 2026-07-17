package com.osir.mcp.models.vps;

import java.util.List;

public class VpsBuildRequest {
    private Integer operatingSystemId;
    private String hostname;
    private List<Integer> sshKeyIds;
    private Double swap;

    public VpsBuildRequest() {}

    public VpsBuildRequest(Integer operatingSystemId, String hostname, List<Integer> sshKeyIds, Double swap) {
        this.operatingSystemId = operatingSystemId;
        this.hostname = hostname;
        this.sshKeyIds = sshKeyIds;
        this.swap = swap;
    }

    public Integer getOperatingSystemId() { return operatingSystemId; }
    public void setOperatingSystemId(Integer operatingSystemId) { this.operatingSystemId = operatingSystemId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public List<Integer> getSshKeyIds() { return sshKeyIds; }
    public void setSshKeyIds(List<Integer> sshKeyIds) { this.sshKeyIds = sshKeyIds; }

    public Double getSwap() { return swap; }
    public void setSwap(Double swap) { this.swap = swap; }
}
