package com.osir.mcp.models;

public class DomainTransferResult {
    private String domain;
    private boolean success;
    private String message;
    private String transactionId;
    private String estimatedCompletionDate;

    public DomainTransferResult(String domain, boolean success, String message) {
        this.domain = domain;
        this.success = success;
        this.message = message;
    }

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getEstimatedCompletionDate() { return estimatedCompletionDate; }
    public void setEstimatedCompletionDate(String estimatedCompletionDate) {
        this.estimatedCompletionDate = estimatedCompletionDate;
    }
}
