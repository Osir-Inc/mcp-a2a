package com.osir.mcp.models.vps;

public class VpsInstanceDetailResult {
    private boolean success;
    private String message;
    private VpsInstanceSummary instance;

    public VpsInstanceDetailResult() {}

    public VpsInstanceDetailResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public VpsInstanceSummary getInstance() { return instance; }
    public void setInstance(VpsInstanceSummary instance) { this.instance = instance; }
}
