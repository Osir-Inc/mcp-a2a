package com.osir.mcp.models.billing;

/** Body of POST /v1/payment/topups. {@code source} is fixed, not an agent-facing parameter. */
public class TopUpRequest {
    private String source = "shared_payment_token";
    private String sharedPaymentToken;
    private int creditCents;
    private String currency;
    private Integer maxChargeCents;
    private String description;

    public TopUpRequest() {}

    public TopUpRequest(String sharedPaymentToken, int creditCents, String currency,
                        Integer maxChargeCents, String description) {
        this.sharedPaymentToken = sharedPaymentToken;
        this.creditCents = creditCents;
        this.currency = currency;
        this.maxChargeCents = maxChargeCents;
        this.description = description;
    }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getSharedPaymentToken() { return sharedPaymentToken; }
    public void setSharedPaymentToken(String sharedPaymentToken) { this.sharedPaymentToken = sharedPaymentToken; }
    public int getCreditCents() { return creditCents; }
    public void setCreditCents(int creditCents) { this.creditCents = creditCents; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Integer getMaxChargeCents() { return maxChargeCents; }
    public void setMaxChargeCents(Integer maxChargeCents) { this.maxChargeCents = maxChargeCents; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
