package com.osir.mcp.models.domain;

public class DomainTransferResponse {
    private String domain;
    private boolean success;
    private String status;
    private String transactionId;
    private String message;
    private String errorCode;
    private String estimatedCompletionDate;

    public DomainTransferResponse() {}

    public DomainTransferResponse(String domain, boolean success, String status) {
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

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getEstimatedCompletionDate() { return estimatedCompletionDate; }
    public void setEstimatedCompletionDate(String estimatedCompletionDate) {
        this.estimatedCompletionDate = estimatedCompletionDate;
    }
}
