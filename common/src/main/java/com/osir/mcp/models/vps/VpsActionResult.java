package com.osir.mcp.models.vps;

public class VpsActionResult {
    private boolean success;
    private String message;
    private String instanceId;
    private String status;

    public VpsActionResult() {}

    public VpsActionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
