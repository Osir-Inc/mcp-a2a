package com.osir.mcp.models;

public class ContactUpdateResponse {
    private String domain;
    private boolean success;
    private String status;
    private String message;
    private String errorCode;

    public ContactUpdateResponse() {}

    public ContactUpdateResponse(String domain, boolean success, String status) {
        this.domain = domain;
        this.success = success;
        this.status = status;
    }

    // Getters and Setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
