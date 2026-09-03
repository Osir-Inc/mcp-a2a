package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainInfoBackendResponse {
    private String domain;
    private String environment;
    private String tenant;
    private String registrar;
    private String timestamp;
    private boolean success;
    private String status;
    private String message;
    private DomainData data;

    /**
     * DomainInfoResponseDTO (backend v2.11.5) - field names verified against backend source,
     * see docs/agent-readiness/BACKEND-CONTRACT-lock-and-info.md. Notably: the privacy flag is
     * named "privacy" (not privacyProtection) and the dates are "creationDate"/"expiryDate"
     * (not crDate/exDate) - both were mismapped before, yielding false/null in getDomainInfo.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainData {
        // "status" is a single string (e.g. "active", "transferredOut"); "statuses" is the EPP array
        private String status;
        private List<String> statuses;
        private String creationDate;
        private String expiryDate;
        private List<String> nameservers;
        private Boolean autoRenew;
        private Boolean privacy;
        private Boolean locked;
        private Boolean premium;
        private Boolean expired;
        private Boolean inRedemptionPeriod;
        private String rgpStatus;
        private String registrar;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<String> getStatuses() { return statuses; }
        public void setStatuses(List<String> statuses) { this.statuses = statuses; }

        public String getCreationDate() { return creationDate; }
        public void setCreationDate(String creationDate) { this.creationDate = creationDate; }

        public String getExpiryDate() { return expiryDate; }
        public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }

        public List<String> getNameservers() { return nameservers; }
        public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

        public Boolean getAutoRenew() { return autoRenew; }
        public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

        public Boolean getPrivacy() { return privacy; }
        public void setPrivacy(Boolean privacy) { this.privacy = privacy; }

        public Boolean getLocked() { return locked; }
        public void setLocked(Boolean locked) { this.locked = locked; }

        public Boolean getPremium() { return premium; }
        public void setPremium(Boolean premium) { this.premium = premium; }

        public Boolean getExpired() { return expired; }
        public void setExpired(Boolean expired) { this.expired = expired; }

        public Boolean getInRedemptionPeriod() { return inRedemptionPeriod; }
        public void setInRedemptionPeriod(Boolean inRedemptionPeriod) { this.inRedemptionPeriod = inRedemptionPeriod; }

        public String getRgpStatus() { return rgpStatus; }
        public void setRgpStatus(String rgpStatus) { this.rgpStatus = rgpStatus; }

        public String getRegistrar() { return registrar; }
        public void setRegistrar(String registrar) { this.registrar = registrar; }
    }

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    
    public String getRegistrar() { return registrar; }
    public void setRegistrar(String registrar) { this.registrar = registrar; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public DomainData getData() { return data; }
    public void setData(DomainData data) { this.data = data; }
}