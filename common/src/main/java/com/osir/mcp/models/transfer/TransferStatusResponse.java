package com.osir.mcp.models.transfer;

public class TransferStatusResponse {
    private String domain;
    private String status;
    private String requestDate;
    private String currentRegistrar;
    private String expectedCompletion;

    public TransferStatusResponse() {}

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
