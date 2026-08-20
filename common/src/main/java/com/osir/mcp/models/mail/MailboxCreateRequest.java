package com.osir.mcp.models.mail;

/** Body for POST /v1/hosting/mail/domains/{domain}/mailboxes. packageId is required — no default tier. */
public class MailboxCreateRequest {
    private String localPart;
    private String packageId;
    private String term;

    public MailboxCreateRequest() {}

    public MailboxCreateRequest(String localPart, String packageId, String term) {
        this.localPart = localPart;
        this.packageId = packageId;
        this.term = term;
    }

    public String getLocalPart() { return localPart; }
    public void setLocalPart(String localPart) { this.localPart = localPart; }

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getTerm() { return term; }
    public void setTerm(String term) { this.term = term; }
}
