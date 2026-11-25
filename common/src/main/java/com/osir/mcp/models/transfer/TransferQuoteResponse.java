package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Backend {@code DomainTransferQuote} from {@code GET /v2/transfer/{domain}/quote}. Money is in
 * cents; {@code totalFees} (= totalCost + ICANN + service fee) is what initiate actually charges.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferQuoteResponse {
    private String domain;
    private Integer totalFees;
    private String currency;
    private Integer extensionYears;
    private String quoteExpiry;

    public TransferQuoteResponse() {}

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public Integer getTotalFees() { return totalFees; }
    public void setTotalFees(Integer totalFees) { this.totalFees = totalFees; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getExtensionYears() { return extensionYears; }
    public void setExtensionYears(Integer extensionYears) { this.extensionYears = extensionYears; }

    public String getQuoteExpiry() { return quoteExpiry; }
    public void setQuoteExpiry(String quoteExpiry) { this.quoteExpiry = quoteExpiry; }
}
