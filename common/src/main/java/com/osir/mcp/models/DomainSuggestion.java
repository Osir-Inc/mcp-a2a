package com.osir.mcp.models;

public class DomainSuggestion {
    private String domain;
    private boolean available;
    private Double price;
    private String currency;

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
