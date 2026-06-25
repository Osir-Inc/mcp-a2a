package com.osir.mcp.models;

import java.util.List;

public class NameserverUpdateResult {
    private String domain;
    private boolean success;
    private String message;
    private List<String> updatedNameservers;

    public NameserverUpdateResult(String domain, boolean success, String message) {
        this.domain = domain;
        this.success = success;
        this.message = message;
    }

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<String> getUpdatedNameservers() { return updatedNameservers; }
    public void setUpdatedNameservers(List<String> updatedNameservers) {
        this.updatedNameservers = updatedNameservers;
    }
}
