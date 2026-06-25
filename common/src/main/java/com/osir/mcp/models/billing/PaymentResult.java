package com.osir.mcp.models.billing;

public class PaymentResult {
    private boolean success;
    private String message;
    private String invoiceNumber;
    private String amountPaid;
    private String remainingBalance;

    public PaymentResult() {}

    public PaymentResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getAmountPaid() { return amountPaid; }
    public void setAmountPaid(String amountPaid) { this.amountPaid = amountPaid; }

    public String getRemainingBalance() { return remainingBalance; }
    public void setRemainingBalance(String remainingBalance) { this.remainingBalance = remainingBalance; }
}
