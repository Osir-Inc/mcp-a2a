package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * autoRenew/privacyProtection are real account values from the backend (same DomainRegistry
 * source as listUserDomains - contract in docs/agent-readiness/BACKEND-CONTRACT-lock-and-info.md).
 * Null fields are OMITTED: dates are null while a registration is still pending at the registry,
 * and autoRenew is suppressed for transferredOut domains (the backend hardcodes false there).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DomainInfoResult {
    private String domain;
    private boolean success;
    private String message;
    private String status;
    private List<String> statuses;
    private String registrationDate;
    private String expirationDate;
    private List<String> nameservers;
    private Boolean autoRenew;
    private Boolean privacyProtection;
    private Boolean locked;
    private Boolean premium;
    private Boolean expired;
    private Boolean inRedemptionPeriod;
    private String registrar;

    public DomainInfoResult(String domain, boolean success, String message) {
        this.domain = domain;
        this.success = success;
        this.message = message;
    }

    public DomainInfoResult(DomainInfoBackendResponse response) {
        this.domain = response.getDomain();
        this.success = response.isSuccess();
        this.message = response.getMessage();

        DomainInfoBackendResponse.DomainData data = response.getData();
        if (data != null) {
            this.status = data.getStatus();
            this.statuses = data.getStatuses();
            this.registrationDate = data.getCreationDate();
            this.expirationDate = data.getExpiryDate();
            this.nameservers = data.getNameservers();
            this.privacyProtection = data.getPrivacy(); // backend field name: "privacy"
            this.locked = data.getLocked();
            this.premium = data.getPremium();
            this.expired = data.getExpired();
            this.inRedemptionPeriod = data.getInRedemptionPeriod();
            this.registrar = data.getRegistrar();
            // The backend hardcodes autoRenew=false for transferredOut domains; suppress the
            // meaningless value rather than reporting it as a real setting.
            if (!"transferredOut".equals(data.getStatus())) {
                this.autoRenew = data.getAutoRenew();
            }
        }
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
    public Boolean getAutoRenew() { return autoRenew; }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }
    public Boolean getPrivacyProtection() { return privacyProtection; }
    public void setPrivacyProtection(Boolean privacyProtection) { this.privacyProtection = privacyProtection; }
    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean locked) { this.locked = locked; }
    public Boolean getPremium() { return premium; }
    public void setPremium(Boolean premium) { this.premium = premium; }
    public Boolean getExpired() { return expired; }
    public void setExpired(Boolean expired) { this.expired = expired; }
    public Boolean getInRedemptionPeriod() { return inRedemptionPeriod; }
    public void setInRedemptionPeriod(Boolean inRedemptionPeriod) { this.inRedemptionPeriod = inRedemptionPeriod; }
    public String getRegistrar() { return registrar; }
    public void setRegistrar(String registrar) { this.registrar = registrar; }
}
