package com.osir.mcp.models;

import java.util.List;

public class DomainInfoResult {
    private String domain;
    private boolean success;
    private String message;
    private String status;
    private List<String> statuses;
    private String registrationDate;
    private String expirationDate;
    private List<String> nameservers;
    private boolean autoRenew;
    private boolean privacyProtection;

    public DomainInfoResult(String domain, boolean success, String message) {
        this.domain = domain;
        this.success = success;
        this.message = message;
    }

    public DomainInfoResult(DomainInfoBackendResponse response) {
        this.domain = response.getDomain();
        this.success = response.isSuccess();
        this.message = response.getMessage();

        if (response.getData() != null) {
            this.status = response.getData().getStatus();
            this.statuses = response.getData().getStatuses();
            this.registrationDate = response.getData().getCrDate();
            this.expirationDate = response.getData().getExDate();
            this.nameservers = response.getData().getNameservers();
        }

        this.autoRenew = false;
        this.privacyProtection = false;
    }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
    public String getRegistrationDate() { return registrationDate; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }
    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }
    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }
    public boolean isAutoRenew() { return autoRenew; }
    public void setAutoRenew(boolean autoRenew) { this.autoRenew = autoRenew; }
    public boolean isPrivacyProtection() { return privacyProtection; }
    public void setPrivacyProtection(boolean privacyProtection) { this.privacyProtection = privacyProtection; }
}
