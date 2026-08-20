package com.osir.mcp.models.mail;

import java.util.List;

public class MailDomainListResult {
    private boolean success;
    private String message;
    private List<MailDomainInfo> domains;

    public MailDomainListResult() {}

    public MailDomainListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<MailDomainInfo> getDomains() { return domains; }
    public void setDomains(List<MailDomainInfo> domains) { this.domains = domains; }
}
