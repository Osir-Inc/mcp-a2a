package com.osir.mcp.models;

public class DomainRenewalRequest {
    private String domain;
    private int years;
    private boolean autoRenew;

    public DomainRenewalRequest() {}

    public DomainRenewalRequest(String domain, int years) {
        this.domain = domain;
        this.years = years;
        this.autoRenew = true; // Default to true
    }

    // Getters and Setters
    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public int getYears() {
        return years;
    }

    public void setYears(int years) {
        this.years = years;
    }

    public boolean isAutoRenew() {
        return autoRenew;
    }

    public void setAutoRenew(boolean autoRenew) {
        this.autoRenew = autoRenew;
    }
}
