package com.osir.mcp.models.billing;

public class FeePreviewResponse {
    private String amount;
    private String fee;
    private String total;
    private String currency;

    public FeePreviewResponse() {}

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getFee() { return fee; }
    public void setFee(String fee) { this.fee = fee; }

    public String getTotal() { return total; }
    public void setTotal(String total) { this.total = total; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
