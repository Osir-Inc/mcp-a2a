package com.osir.mcp.models.domain;

import com.osir.mcp.models.contact.RegistrantInfo;

import java.util.List;

// Transfer Models
public class DomainTransferRequest {
    private String domain;
    private String authCode;
    private RegistrantInfo registrant;
    private List<String> nameservers;
    private boolean autoRenew;

    public DomainTransferRequest() {}

    public DomainTransferRequest(String domain, String authCode) {
        this.domain = domain;
        this.authCode = authCode;
        this.autoRenew = true;
    }

    // Getters and Setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }

    public RegistrantInfo getRegistrant() { return registrant; }
    public void setRegistrant(RegistrantInfo registrant) { this.registrant = registrant; }

    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
}
