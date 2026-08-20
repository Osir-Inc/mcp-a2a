package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** One row from GET /v1/hosting/mail/mailboxes. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailboxSummary {
    private String id;
    private String emailAddress;
    // The backend calls this field "package", which is a Java keyword.
    @JsonProperty("package")
    private String packageName;
    private String paymentTerm;
    private String status;
    private String nextRenewalDate;

    public MailboxSummary() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNextRenewalDate() { return nextRenewalDate; }
    public void setNextRenewalDate(String nextRenewalDate) { this.nextRenewalDate = nextRenewalDate; }
}
