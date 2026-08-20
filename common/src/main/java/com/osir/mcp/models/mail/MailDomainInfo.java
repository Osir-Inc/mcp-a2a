package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A domain provisioned for email hosting. Status: PENDING_DNS until DNS verifies, then ACTIVE. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailDomainInfo {
    private String fqdn;
    private String dnsMode;
    private String status;
    private String createdAt;
    private String updatedAt;

    public MailDomainInfo() {}

    public String getFqdn() { return fqdn; }
    public void setFqdn(String fqdn) { this.fqdn = fqdn; }

    public String getDnsMode() { return dnsMode; }
    public void setDnsMode(String dnsMode) { this.dnsMode = dnsMode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
