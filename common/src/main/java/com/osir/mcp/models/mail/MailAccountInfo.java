package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The account object inside a mailbox-create response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailAccountInfo {
    private String id;
    private String localPart;
    private String paymentTerm;
    private String status;
    private String nextRenewalDate;

    public MailAccountInfo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLocalPart() { return localPart; }
    public void setLocalPart(String localPart) { this.localPart = localPart; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNextRenewalDate() { return nextRenewalDate; }
    public void setNextRenewalDate(String nextRenewalDate) { this.nextRenewalDate = nextRenewalDate; }
}
