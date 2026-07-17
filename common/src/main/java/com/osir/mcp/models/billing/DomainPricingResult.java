package com.osir.mcp.models.billing;

import java.util.List;

public class DomainPricingResult {
    private boolean success;
    private String message;
    private List<PricingEntry> pricing;

    public DomainPricingResult() {}

    public DomainPricingResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<PricingEntry> getPricing() { return pricing; }
    public void setPricing(List<PricingEntry> pricing) { this.pricing = pricing; }
}
