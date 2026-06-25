package com.osir.mcp.models.transfer;

public class TransferStatusResult {
    private boolean success;
    private String message;
    private String domain;
    private String status;
    private String requestDate;
    private String currentRegistrar;
    private String expectedCompletion;

    public TransferStatusResult() {}

    public TransferStatusResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRequestDate() { return requestDate; }
    public void setRequestDate(String requestDate) { this.requestDate = requestDate; }

    public String getCurrentRegistrar() { return currentRegistrar; }
    public void setCurrentRegistrar(String currentRegistrar) { this.currentRegistrar = currentRegistrar; }

    public String getExpectedCompletion() { return expectedCompletion; }
    public void setExpectedCompletion(String expectedCompletion) { this.expectedCompletion = expectedCompletion; }
}
