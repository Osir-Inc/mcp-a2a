package com.osir.mcp.models;

public class PrivacyResponse {
    private String domain;
    private boolean privacyEnabled;
    private String status;
    private String message;
    private String errorCode;

    public PrivacyResponse() {}

    public PrivacyResponse(String domain, boolean privacyEnabled, String status) {
        this.domain = domain;
        this.privacyEnabled = privacyEnabled;
        this.status = status;
    }

    // Getters and Setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean isPrivacyEnabled() { return privacyEnabled; }
    public void setPrivacyEnabled(boolean privacyEnabled) { this.privacyEnabled = privacyEnabled; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
