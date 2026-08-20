package com.osir.mcp.models.mail;

/** Body for POST /v1/hosting/mail/domains/{domain}. */
public class MailEnableDomainRequest {
    private String dnsMode;
    private boolean spfMergeConfirmed;
    private boolean takeoverConfirmed;

    public MailEnableDomainRequest() {}

    public MailEnableDomainRequest(String dnsMode, boolean spfMergeConfirmed, boolean takeoverConfirmed) {
        this.dnsMode = dnsMode;
        this.spfMergeConfirmed = spfMergeConfirmed;
        this.takeoverConfirmed = takeoverConfirmed;
    }

    public String getDnsMode() { return dnsMode; }
    public void setDnsMode(String dnsMode) { this.dnsMode = dnsMode; }

    public boolean isSpfMergeConfirmed() { return spfMergeConfirmed; }
    public void setSpfMergeConfirmed(boolean spfMergeConfirmed) { this.spfMergeConfirmed = spfMergeConfirmed; }

    public boolean isTakeoverConfirmed() { return takeoverConfirmed; }
    public void setTakeoverConfirmed(boolean takeoverConfirmed) { this.takeoverConfirmed = takeoverConfirmed; }
}
