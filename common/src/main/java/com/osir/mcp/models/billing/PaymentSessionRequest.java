package com.osir.mcp.models.billing;

public class PaymentSessionRequest {
    private String processor;
    private double amount;
    private String currency;
    private String description;
    private String successUrl;
    private String cancelUrl;

    public PaymentSessionRequest() {}

    public PaymentSessionRequest(String processor, double amount, String currency, String successUrl, String cancelUrl) {
        this.processor = processor;
        this.amount = amount;
        this.currency = currency;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;
    }

    public String getProcessor() { return processor; }
    public void setProcessor(String processor) { this.processor = processor; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSuccessUrl() { return successUrl; }
    public void setSuccessUrl(String successUrl) { this.successUrl = successUrl; }

    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl; }
}
