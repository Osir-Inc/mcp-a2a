package com.osir.mcp.models.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps GET /v1/payment/balance.
 * Wire format: { success, data: { balance, balanceCents, currency } }
 * Getters fall back to top-level balance/currency for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BalanceResponse {

    private boolean success;
    private BalanceData data;

    // Top-level fallback fields (in case API returns them flat)
    private String balance;
    private String currency;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public BalanceData getData() { return data; }
    public void setData(BalanceData data) { this.data = data; }

    public String getBalance() {
        if (data != null && data.getBalance() != null) return data.getBalance();
        return balance;
    }
    public void setBalance(String balance) { this.balance = balance; }

    public String getCurrency() {
        if (data != null && data.getCurrency() != null) return data.getCurrency();
        return currency;
    }
    public void setCurrency(String currency) { this.currency = currency; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BalanceData {
        private String balance;
        private Integer balanceCents;
        private String currency;

        public String getBalance() {
            if (balance != null) return balance;
            if (balanceCents != null) return String.format(java.util.Locale.US, "%.2f", balanceCents / 100.0);
            return null;
        }
        public void setBalance(String balance) { this.balance = balance; }

        public Integer getBalanceCents() { return balanceCents; }
        public void setBalanceCents(Integer balanceCents) { this.balanceCents = balanceCents; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
    }
}
