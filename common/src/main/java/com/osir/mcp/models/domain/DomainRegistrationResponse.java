package com.osir.mcp.models.domain;

import java.util.List;

public class DomainRegistrationResponse {
    private String domain;
    private boolean success;
    private String status;
    private String transactionId;
    private String message;
    private Double totalCost;
    private String currency;
    private String expirationDate;
    private List<String> nameservers;
    private String errorCode;

    public DomainRegistrationResponse() {}

    public DomainRegistrationResponse(String domain, boolean success, String status) {
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

    public Double getTotalCost() { return totalCost; }
    public void setTotalCost(Double totalCost) { this.totalCost = totalCost; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }

    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
