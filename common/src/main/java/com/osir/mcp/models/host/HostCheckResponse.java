package com.osir.mcp.models.host;

public class HostCheckResponse {
    private boolean available;
    private String hostname;
    private String message;

    public HostCheckResponse() {}

    public HostCheckResponse(boolean available, String hostname, String message) {
        this.available = available;
        this.hostname = hostname;
        this.message = message;
    }

    // Getters and Setters
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
