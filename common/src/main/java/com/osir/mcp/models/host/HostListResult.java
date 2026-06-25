package com.osir.mcp.models.host;

import java.util.List;

public class HostListResult {
    private boolean success;
    private String message;
    private List<HostRecord> hosts;

    public HostListResult() {}

    public HostListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public HostListResult(boolean success, String message, List<HostRecord> hosts) {
        this.success = success;
        this.message = message;
        this.hosts = hosts;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<HostRecord> getHosts() { return hosts; }
    public void setHosts(List<HostRecord> hosts) { this.hosts = hosts; }
}
