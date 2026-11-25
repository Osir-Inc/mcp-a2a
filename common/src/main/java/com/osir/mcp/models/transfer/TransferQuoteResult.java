package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonInclude;

// NON_NULL: the generated output schema types every field, so a null one fails validation.
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransferQuoteResult {
    private boolean success;
    private String message;
    private String domain;
    private String transferPrice;
    private String currency;
    private Integer extensionYears;
    private String quoteExpiry;

    public TransferQuoteResult() {}

    public TransferQuoteResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getTransferPrice() { return transferPrice; }
    public void setTransferPrice(String transferPrice) { this.transferPrice = transferPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Integer getExtensionYears() { return extensionYears; }
    public void setExtensionYears(Integer extensionYears) { this.extensionYears = extensionYears; }

    public String getQuoteExpiry() { return quoteExpiry; }
    public void setQuoteExpiry(String quoteExpiry) { this.quoteExpiry = quoteExpiry; }
}
