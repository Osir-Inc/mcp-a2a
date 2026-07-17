package com.osir.mcp.models.auth;

public class DeviceLoginStatusResult {
    private boolean success;
    private String message;
    private String status; // pending, complete, expired, denied, slow_down
    private Long expiresIn;
    private String tokenType;

    public DeviceLoginStatusResult(boolean success, String message, String status) {
        this.success = success;
        this.message = message;
        this.status = status;
    }

    public DeviceLoginStatusResult(boolean success, String message, String status,
                                   Long expiresIn, String tokenType) {
        this.success = success;
        this.message = message;
        this.status = status;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
}
