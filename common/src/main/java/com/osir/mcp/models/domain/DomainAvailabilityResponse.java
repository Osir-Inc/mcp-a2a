package com.osir.mcp.models.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainAvailabilityResponse {
    private String domain;
    private boolean available;
    private String status;
    // API sends cents (e.g. 899 = $8.99)
    @JsonProperty("price")
    private Integer priceCents;
    @JsonProperty("totalPrice")
    private Integer totalPriceCents;
    private String currency;
    private String reason;
    private boolean isPremium;

    public DomainAvailabilityResponse() {}

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Double getPrice() { return priceCents != null ? priceCents / 100.0 : null; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public Double getTotalPrice() { return totalPriceCents != null ? totalPriceCents / 100.0 : null; }
    public void setTotalPriceCents(Integer totalPriceCents) { this.totalPriceCents = totalPriceCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
}
