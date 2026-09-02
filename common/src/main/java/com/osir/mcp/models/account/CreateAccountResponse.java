package com.osir.mcp.models.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/** 201 body of POST /v1/public/account. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateAccountResponse {

    private String accountId;
    private String contactId;
    private String status;                    // PENDING_VERIFICATION
    private Map<String, Object> verification; // {method, sentTo, expiresAt}
    private List<String> nextSteps;

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getContactId() { return contactId; }
    public void setContactId(String contactId) { this.contactId = contactId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Map<String, Object> getVerification() { return verification; }
    public void setVerification(Map<String, Object> verification) { this.verification = verification; }
    public List<String> getNextSteps() { return nextSteps; }
    public void setNextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; }
}
