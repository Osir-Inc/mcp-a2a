package com.osir.mcp.models.contact;

public class ContactActionResponse {
    private boolean success;
    private String message;

    public ContactActionResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
