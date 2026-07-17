package com.osir.mcp.models.transfer;

public class TransferActionResponse {
    private boolean success;
    private String message;

    public TransferActionResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
