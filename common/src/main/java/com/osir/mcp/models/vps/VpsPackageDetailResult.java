package com.osir.mcp.models.vps;

public class VpsPackageDetailResult {
    private boolean success;
    private String message;
    private VpsPackageSummary packageDetail;

    public VpsPackageDetailResult() {}

    public VpsPackageDetailResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public VpsPackageSummary getPackageDetail() { return packageDetail; }
    public void setPackageDetail(VpsPackageSummary packageDetail) { this.packageDetail = packageDetail; }
}
