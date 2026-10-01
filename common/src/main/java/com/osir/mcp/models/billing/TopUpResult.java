package com.osir.mcp.models.billing;

/** What the agent sees back from fundBalanceWithSharedPaymentToken. */
public class TopUpResult {
    private boolean success;
    private String message;
    private String status;
    private String sessionId;
    private Integer creditedCents;
    private Integer chargedCents;
    private Integer processingFeeCents;
    private String currency;
    private Integer balanceCents;
    private String pollEndpoint;
    private String errorCode;
    private String nextStep;

    public TopUpResult() {}

    public TopUpResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getCreditedCents() { return creditedCents; }
    public void setCreditedCents(Integer creditedCents) { this.creditedCents = creditedCents; }
    public Integer getChargedCents() { return chargedCents; }
    public void setChargedCents(Integer chargedCents) { this.chargedCents = chargedCents; }
    public Integer getProcessingFeeCents() { return processingFeeCents; }
    public void setProcessingFeeCents(Integer processingFeeCents) { this.processingFeeCents = processingFeeCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getBalanceCents() { return balanceCents; }
    public void setBalanceCents(Integer balanceCents) { this.balanceCents = balanceCents; }
    public String getPollEndpoint() { return pollEndpoint; }
    public void setPollEndpoint(String pollEndpoint) { this.pollEndpoint = pollEndpoint; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getNextStep() { return nextStep; }
    public void setNextStep(String nextStep) { this.nextStep = nextStep; }
}
