package com.osir.mcp.models;

public class DomainAvailabilityResult {
    private String domain;
    private boolean available;
    private String message;
    private Double price;
    private String currency;
    private boolean isPremium;

    public DomainAvailabilityResult(String domain, boolean available, String message) {
        this.domain = domain;
        this.available = available;
        this.message = message;
    }

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean isPremium() { return isPremium; }
    public void setPremium(boolean premium) { isPremium = premium; }
}
