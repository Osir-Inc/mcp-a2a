package com.osir.mcp.models;

public class TransferStatusResponse {
    private String domain;
    private String status;
    private String statusDescription;
    private String estimatedCompletionDate;
    private String transactionId;

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStatusDescription() { return statusDescription; }
    public void setStatusDescription(String statusDescription) { this.statusDescription = statusDescription; }
    public String getEstimatedCompletionDate() { return estimatedCompletionDate; }
    public void setEstimatedCompletionDate(String estimatedCompletionDate) { this.estimatedCompletionDate = estimatedCompletionDate; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
}
