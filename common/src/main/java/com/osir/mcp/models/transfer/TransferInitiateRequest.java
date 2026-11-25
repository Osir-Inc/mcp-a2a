package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.osir.mcp.models.contact.RegistrantInfo;

import java.util.Map;

/**
 * Backend DomainTransferRequest. Owner: registrantId (saved contact, also admin/tech/billing) wins; else the
 * inline registrant; with neither the backend silently uses the account's default contact.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferInitiateRequest {
    private String domain;
    private String authCode;
    private Long registrantId;
    private Map<String, Object> registrant;

    public TransferInitiateRequest() {}

    public TransferInitiateRequest(String domain, String authCode) {
        this.domain = domain;
        this.authCode = authCode;
    }

    public TransferInitiateRequest(String domain, String authCode, Long registrantId, RegistrantInfo registrant) {
        this(domain, authCode);
        this.registrantId = registrantId;
        this.registrant = registrant == null ? null : registrant.toBackend();
    }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public Long getRegistrantId() { return registrantId; }
    public void setRegistrantId(Long registrantId) { this.registrantId = registrantId; }

    public Map<String, Object> getRegistrant() { return registrant; }
    public void setRegistrant(Map<String, Object> registrant) { this.registrant = registrant; }
}
