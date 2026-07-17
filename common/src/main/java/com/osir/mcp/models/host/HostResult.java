package com.osir.mcp.models.host;

public class HostResult {
    private boolean success;
    private String message;
    private HostRecord record;

    public HostResult() {}

    public HostResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public HostResult(boolean success, String message, HostRecord record) {
        this.success = success;
        this.message = message;
        this.record = record;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public HostRecord getRecord() { return record; }
    public void setRecord(HostRecord record) { this.record = record; }
}
