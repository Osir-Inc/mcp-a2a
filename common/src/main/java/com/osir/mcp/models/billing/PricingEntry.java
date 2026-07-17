package com.osir.mcp.models.billing;

public class PricingEntry {
    private String tld;
    private String operation;
    private String price1Year;
    private String price2Year;
    private String price3Year;
    private String currency;

    public PricingEntry() {}

    public String getTld() { return tld; }
    public void setTld(String tld) { this.tld = tld; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getPrice1Year() { return price1Year; }
    public void setPrice1Year(String price1Year) { this.price1Year = price1Year; }

    public String getPrice2Year() { return price2Year; }
    public void setPrice2Year(String price2Year) { this.price2Year = price2Year; }

    public String getPrice3Year() { return price3Year; }
    public void setPrice3Year(String price3Year) { this.price3Year = price3Year; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
