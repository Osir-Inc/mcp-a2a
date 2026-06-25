package com.osir.mcp.models.host;

public class HostCheckResult {
    private boolean success;
    private String message;
    private boolean available;
    private String hostname;

    public HostCheckResult() {}

    public HostCheckResult(boolean success, String message, boolean available, String hostname) {
        this.success = success;
        this.message = message;
        this.available = available;
        this.hostname = hostname;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }
}
