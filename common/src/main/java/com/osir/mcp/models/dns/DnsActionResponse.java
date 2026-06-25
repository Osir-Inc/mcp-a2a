package com.osir.mcp.models.dns;

public class DnsActionResponse {
    private boolean success;
    private String message;

    public DnsActionResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
