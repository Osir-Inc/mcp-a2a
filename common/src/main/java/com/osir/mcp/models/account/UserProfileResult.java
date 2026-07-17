package com.osir.mcp.models.account;

public class UserProfileResult {
    private boolean success;
    private String message;
    private String customerId;
    private String name;
    private String email;
    private String organization;
    private String balance;
    private String currency;
    private int domainCount;
    private int vpsCount;

    public UserProfileResult() {}

    public UserProfileResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getDomainCount() { return domainCount; }
    public void setDomainCount(int domainCount) { this.domainCount = domainCount; }

    public int getVpsCount() { return vpsCount; }
    public void setVpsCount(int vpsCount) { this.vpsCount = vpsCount; }
}
