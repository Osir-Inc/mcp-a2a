package com.osir.mcp.models.vps;

import java.util.List;

public class VpsInstanceListResult {
    private boolean success;
    private String message;
    private List<VpsInstanceSummary> instances;
    private int totalCount;

    public VpsInstanceListResult() {}

    public VpsInstanceListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<VpsInstanceSummary> getInstances() { return instances; }
    public void setInstances(List<VpsInstanceSummary> instances) { this.instances = instances; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
}
