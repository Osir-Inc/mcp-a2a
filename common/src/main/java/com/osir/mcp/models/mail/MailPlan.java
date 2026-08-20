package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A mailbox package from GET /v1/hosting/mail/plans. Prices come from the backend — never hardcoded. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailPlan {
    private String packageId;
    private String name;
    private String description;
    private Double quotaGb;
    private Long quotaBytes;
    private Integer monthlyCents;
    private Integer annualCents;

    public MailPlan() {}

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Double getQuotaGb() { return quotaGb; }
    public void setQuotaGb(Double quotaGb) { this.quotaGb = quotaGb; }

    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }

    public Integer getMonthlyCents() { return monthlyCents; }
    public void setMonthlyCents(Integer monthlyCents) { this.monthlyCents = monthlyCents; }

    public Integer getAnnualCents() { return annualCents; }
    public void setAnnualCents(Integer annualCents) { this.annualCents = annualCents; }
}
