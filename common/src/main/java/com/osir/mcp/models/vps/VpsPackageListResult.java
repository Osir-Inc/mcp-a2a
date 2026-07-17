package com.osir.mcp.models.vps;

import java.util.List;

public class VpsPackageListResult {
    private boolean success;
    private String message;
    private List<VpsPackageSummary> packages;

    public VpsPackageListResult() {}

    public VpsPackageListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<VpsPackageSummary> getPackages() { return packages; }
    public void setPackages(List<VpsPackageSummary> packages) { this.packages = packages; }
}
