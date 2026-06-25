package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Locale;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainExtension {

    // Identity
    @JsonProperty("extension")
    private String tld;
    @JsonProperty("registryName")
    private String registry;
    @JsonProperty("extensionType")
    private String extensionType;   // "gTLD" | "ccTLD" | "geoTLD" | "sTLD" | "IDN"

    // Pricing — stored as cents, exposed as formatted USD strings
    @JsonProperty("registrationPrice")
    private Integer registrationPriceCents;
    @JsonProperty("renewalPrice")
    private Integer renewalPriceCents;
    @JsonProperty("transferPrice")
    private Integer transferPriceCents;
    @JsonProperty("restorePrice")
    private Integer restorePriceCents;

    // Capabilities
    @JsonProperty("hasPremium")
    private boolean hasPremium;
    @JsonProperty("hasIDN")
    private boolean hasIDN;
    @JsonProperty("hasDNSSEC")
    private boolean hasDNSSEC;
    @JsonProperty("hasRestrictions")
    private boolean hasRestrictions;
    @JsonProperty("supportsWhoisPrivacy")
    private boolean supportsWhoisPrivacy;
    @JsonProperty("supportsRegistrarLock")
    private boolean supportsRegistrarLock;

    // Technical limits
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("minCharacters")
    private Integer minCharacters;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("maxCharacters")
    private Integer maxCharacters;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("minRegistrationPeriod")
    private Integer minRegistrationPeriod;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("maxRegistrationPeriod")
    private Integer maxRegistrationPeriod;

    // Discovery metadata
    @JsonProperty("description")
    private String description;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("categories")
    private List<String> categories;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("audience")
    private List<String> audience;

    public DomainExtension() {}

    // ── Identity ──────────────────────────────────────────────────────────────

    public String getTld() { return tld; }
    public void setTld(String tld) { this.tld = tld; }

    public String getRegistry() { return registry; }
    public void setRegistry(String registry) { this.registry = registry; }

    public String getExtensionType() { return extensionType; }
    public void setExtensionType(String extensionType) { this.extensionType = extensionType; }

    // ── Pricing ───────────────────────────────────────────────────────────────

    public String getRegistrationPrice() {
        return registrationPriceCents != null
                ? String.format(Locale.US, "%.2f", registrationPriceCents / 100.0) : null;
    }
    public void setRegistrationPriceCents(Integer cents) { this.registrationPriceCents = cents; }

    public String getRenewalPrice() {
        return renewalPriceCents != null
                ? String.format(Locale.US, "%.2f", renewalPriceCents / 100.0) : null;
    }
    public void setRenewalPriceCents(Integer cents) { this.renewalPriceCents = cents; }

    public String getTransferPrice() {
        return transferPriceCents != null
                ? String.format(Locale.US, "%.2f", transferPriceCents / 100.0) : null;
    }
    public void setTransferPriceCents(Integer cents) { this.transferPriceCents = cents; }

    public String getRestorePrice() {
        return restorePriceCents != null
                ? String.format(Locale.US, "%.2f", restorePriceCents / 100.0) : null;
    }
    public void setRestorePriceCents(Integer cents) { this.restorePriceCents = cents; }

    // ── Capabilities ──────────────────────────────────────────────────────────

    public boolean isHasPremium() { return hasPremium; }
    public void setHasPremium(boolean hasPremium) { this.hasPremium = hasPremium; }

    public boolean isHasIDN() { return hasIDN; }
    public void setHasIDN(boolean hasIDN) { this.hasIDN = hasIDN; }

    public boolean isHasDNSSEC() { return hasDNSSEC; }
    public void setHasDNSSEC(boolean hasDNSSEC) { this.hasDNSSEC = hasDNSSEC; }

    public boolean isHasRestrictions() { return hasRestrictions; }
    public void setHasRestrictions(boolean hasRestrictions) { this.hasRestrictions = hasRestrictions; }

    public boolean isSupportsWhoisPrivacy() { return supportsWhoisPrivacy; }
    public void setSupportsWhoisPrivacy(boolean supportsWhoisPrivacy) { this.supportsWhoisPrivacy = supportsWhoisPrivacy; }

    public boolean isSupportsRegistrarLock() { return supportsRegistrarLock; }
    public void setSupportsRegistrarLock(boolean supportsRegistrarLock) { this.supportsRegistrarLock = supportsRegistrarLock; }

    // ── Technical limits ──────────────────────────────────────────────────────

    public Integer getMinCharacters() { return minCharacters; }
    public void setMinCharacters(Integer minCharacters) { this.minCharacters = minCharacters; }

    public Integer getMaxCharacters() { return maxCharacters; }
    public void setMaxCharacters(Integer maxCharacters) { this.maxCharacters = maxCharacters; }

    public Integer getMinRegistrationPeriod() { return minRegistrationPeriod; }
    public void setMinRegistrationPeriod(Integer minRegistrationPeriod) { this.minRegistrationPeriod = minRegistrationPeriod; }

    public Integer getMaxRegistrationPeriod() { return maxRegistrationPeriod; }
    public void setMaxRegistrationPeriod(Integer maxRegistrationPeriod) { this.maxRegistrationPeriod = maxRegistrationPeriod; }

    // ── Discovery metadata ────────────────────────────────────────────────────

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getCategories() { return categories; }
    public void setCategories(List<String> categories) { this.categories = categories; }

    public List<String> getAudience() { return audience; }
    public void setAudience(List<String> audience) { this.audience = audience; }
}
