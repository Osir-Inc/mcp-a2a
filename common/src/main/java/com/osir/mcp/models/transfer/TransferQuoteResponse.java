package com.osir.mcp.models.transfer;

public class TransferQuoteResponse {
    private String domain;
    private String transferPrice;
    private String currency;
    private String extensionYears;
    private String newExpirationDate;

    public TransferQuoteResponse() {}

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getTransferPrice() { return transferPrice; }
    public void setTransferPrice(String transferPrice) { this.transferPrice = transferPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getExtensionYears() { return extensionYears; }
    public void setExtensionYears(String extensionYears) { this.extensionYears = extensionYears; }

    public String getNewExpirationDate() { return newExpirationDate; }
    public void setNewExpirationDate(String newExpirationDate) { this.newExpirationDate = newExpirationDate; }
}
