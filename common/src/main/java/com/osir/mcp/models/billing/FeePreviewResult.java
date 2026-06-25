package com.osir.mcp.models.billing;

public class FeePreviewResult {
    private boolean success;
    private String message;
    private String amount;
    private String fee;
    private String total;
    private String currency;

    public FeePreviewResult() {}

    public FeePreviewResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getFee() { return fee; }
    public void setFee(String fee) { this.fee = fee; }

    public String getTotal() { return total; }
    public void setTotal(String total) { this.total = total; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
