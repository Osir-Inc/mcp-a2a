package com.osir.mcp.models.billing;

public class AccountBalanceResult {
    private boolean success;
    private String message;
    private String balance;
    private String currency;

    public AccountBalanceResult() {}

    public AccountBalanceResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
