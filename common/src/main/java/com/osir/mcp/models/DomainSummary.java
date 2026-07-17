package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainSummary {
    private long id;
    private String domain;
    private String expirationDate;
    private String creationDate;
    private String status;
    private String customerId;

    @JsonProperty("autoRenew")
    private boolean autorenew;

    private boolean privacy;
    private List<String> nameservers;
    private List<String> statuses;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getExpirationDate() { return expirationDate; }
    public void setExpirationDate(String expirationDate) { this.expirationDate = expirationDate; }

    public String getCreationDate() { return creationDate; }
    public void setCreationDate(String creationDate) { this.creationDate = creationDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public boolean isAutorenew() { return autorenew; }
    public void setAutorenew(boolean autorenew) { this.autorenew = autorenew; }

    public boolean isPrivacy() { return privacy; }
    public void setPrivacy(boolean privacy) { this.privacy = privacy; }

    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

    public List<String> getStatuses() { return statuses; }
    public void setStatuses(List<String> statuses) { this.statuses = statuses; }
}
