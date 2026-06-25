package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

public class CategorizedTldsFilters {
    private boolean excludeRestricted;
    private boolean excludeCcTLDs;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Double maxRegisterPrice;
    private boolean excludePremium;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String registry;

    public CategorizedTldsFilters() {}

    public CategorizedTldsFilters(boolean excludeRestricted, boolean excludeCcTLDs,
                                   Double maxRegisterPrice, boolean excludePremium, String registry) {
        this.excludeRestricted = excludeRestricted;
        this.excludeCcTLDs = excludeCcTLDs;
        this.maxRegisterPrice = maxRegisterPrice;
        this.excludePremium = excludePremium;
        this.registry = registry;
    }

    public boolean isExcludeRestricted() { return excludeRestricted; }
    public void setExcludeRestricted(boolean excludeRestricted) { this.excludeRestricted = excludeRestricted; }

    public boolean isExcludeCcTLDs() { return excludeCcTLDs; }
    public void setExcludeCcTLDs(boolean excludeCcTLDs) { this.excludeCcTLDs = excludeCcTLDs; }

    public Double getMaxRegisterPrice() { return maxRegisterPrice; }
    public void setMaxRegisterPrice(Double maxRegisterPrice) { this.maxRegisterPrice = maxRegisterPrice; }

    public boolean isExcludePremium() { return excludePremium; }
    public void setExcludePremium(boolean excludePremium) { this.excludePremium = excludePremium; }

    public String getRegistry() { return registry; }
    public void setRegistry(String registry) { this.registry = registry; }
}
