package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class CategorizedTldCandidate {
    private String tld;
    private List<String> categories;
    private List<String> audience;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String registrationPrice;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String renewalPrice;
    private String currency = "USD";
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String extensionType;
    private boolean hasRestrictions;
    private boolean hasPremium;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String registryName;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer minRegistrationPeriod;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer maxRegistrationPeriod;

    public CategorizedTldCandidate() {}

    public String getTld() { return tld; }
    public void setTld(String tld) { this.tld = tld; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getAudience() { return audience; }
    public void setAudience(List<String> audience) { this.audience = audience; }

    public String getRegistrationPrice() { return registrationPrice; }
    public void setRegistrationPrice(String registrationPrice) { this.registrationPrice = registrationPrice; }

    public String getRenewalPrice() { return renewalPrice; }
    public void setRenewalPrice(String renewalPrice) { this.renewalPrice = renewalPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getExtensionType() { return extensionType; }
    public void setExtensionType(String extensionType) { this.extensionType = extensionType; }

    public boolean isHasRestrictions() { return hasRestrictions; }
    public void setHasRestrictions(boolean hasRestrictions) { this.hasRestrictions = hasRestrictions; }

    public boolean isHasPremium() { return hasPremium; }
    public void setHasPremium(boolean hasPremium) { this.hasPremium = hasPremium; }

    public String getRegistryName() { return registryName; }
    public void setRegistryName(String registryName) { this.registryName = registryName; }

    public Integer getMinRegistrationPeriod() { return minRegistrationPeriod; }
    public void setMinRegistrationPeriod(Integer minRegistrationPeriod) { this.minRegistrationPeriod = minRegistrationPeriod; }

    public Integer getMaxRegistrationPeriod() { return maxRegistrationPeriod; }
    public void setMaxRegistrationPeriod(Integer maxRegistrationPeriod) { this.maxRegistrationPeriod = maxRegistrationPeriod; }
}
