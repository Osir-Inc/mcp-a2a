package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferStatusResponse {
    private String domain;
    private String status;
    @JsonAlias({"transferInitiatedDate", "createdAt"})
    private String requestDate;
    @JsonAlias("facingRegistrar")
    private String currentRegistrar;
    @JsonAlias({"expectedCompletionDate", "expiresAt"})
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
