package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Deserialized straight from GET /v1/hosting/mail/quote ({packageId, packageName, term, quotaBytes,
 * priceCents, currency}); the service fills success/message. Display-only — the backend re-derives
 * the authoritative price at purchase.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailQuoteResult {
    private boolean success;
    private String message;
    private String packageId;
    private String packageName;
    private String term;
    private Long quotaBytes;
    private Integer priceCents;
    private String currency;

    public MailQuoteResult() {}

    public MailQuoteResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }

    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }

    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
