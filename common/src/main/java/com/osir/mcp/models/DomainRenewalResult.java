package com.osir.mcp.models;

public class DomainRenewalResult {
    private boolean success;
    private String message;
    private String domain;
    private String status;

    public DomainRenewalResult() {}

    public DomainRenewalResult(boolean success, String message, String domain, String status) {
        this.success = success;
        this.message = message;
        this.domain = domain;
        this.status = status;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
