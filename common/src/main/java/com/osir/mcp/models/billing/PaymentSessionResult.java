package com.osir.mcp.models.billing;

public class PaymentSessionResult {
    private boolean success;
    private String message;
    private String sessionId;
    private String checkoutUrl;
    private String expiresAt;
    private String pollTool;
    private String pollEndpoint;

    public PaymentSessionResult() {}

    public PaymentSessionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getCheckoutUrl() { return checkoutUrl; }
    public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getPollTool() { return pollTool; }
    public void setPollTool(String pollTool) { this.pollTool = pollTool; }

    public String getPollEndpoint() { return pollEndpoint; }
    public void setPollEndpoint(String pollEndpoint) { this.pollEndpoint = pollEndpoint; }
}
