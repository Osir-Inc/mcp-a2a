package com.osir.mcp.models.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceInfo {
    // API sends cents (e.g. 1030 = $10.30); divide on read
    @JsonProperty("amount")
    private Integer amountCents;
    private String currency;

    public BalanceInfo() {}

    public Double getAmount() {
        return amountCents != null ? amountCents / 100.0 : null;
    }

    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
