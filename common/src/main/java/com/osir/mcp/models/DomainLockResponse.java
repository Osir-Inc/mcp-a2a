package com.osir.mcp.models;

public class DomainLockResponse {
    private String domain;
    private boolean locked;
    private String status;
    private String message;
    private String errorCode;

    public DomainLockResponse() {}

    public DomainLockResponse(String domain, boolean locked, String status) {
        this.domain = domain;
        this.locked = locked;
        this.status = status;
    }

    // Getters and Setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
