package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Deserialized straight from POST /v1/hosting/mail/domains/{domain} ({domain, dnsRecords, warnings});
 * the service fills success/message. In EXTERNAL_MANUAL mode dnsRecords is what the customer must
 * publish; the domain stays PENDING_DNS until verifyMailDns succeeds.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailDomainEnableResult {
    private boolean success;
    private String message;
    private MailDomainInfo domain;
    private List<MailDnsRecordInfo> dnsRecords;
    private List<String> warnings;

    public MailDomainEnableResult() {}

    public MailDomainEnableResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public MailDomainInfo getDomain() { return domain; }
    public void setDomain(MailDomainInfo domain) { this.domain = domain; }

    public List<MailDnsRecordInfo> getDnsRecords() { return dnsRecords; }
    public void setDnsRecords(List<MailDnsRecordInfo> dnsRecords) { this.dnsRecords = dnsRecords; }

    public List<String> getWarnings() { return warnings; }
    public void setWarnings(List<String> warnings) { this.warnings = warnings; }
}
