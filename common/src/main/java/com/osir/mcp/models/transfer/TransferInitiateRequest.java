package com.osir.mcp.models.transfer;

public class TransferInitiateRequest {
    private String domain;
    private String authCode;

    public TransferInitiateRequest() {}

    public TransferInitiateRequest(String domain, String authCode) {
        this.domain = domain;
        this.authCode = authCode;
    }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
}
