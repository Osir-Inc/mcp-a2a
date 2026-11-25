package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferInitiateResponse {
    private boolean success;
    private String message;
    private String domain;
    @JsonAlias("orderId")
    private String transferId;
    private String status;

    public TransferInitiateResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getTransferId() { return transferId; }
    public void setTransferId(String transferId) { this.transferId = transferId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
