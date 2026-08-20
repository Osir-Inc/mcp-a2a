package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Deserialized straight from POST .../dns-verify ({verified, missing}); the service fills
 * success/message. verified=true means the domain activated.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailDnsVerifyResult {
    private boolean success;
    private String message;
    private Boolean verified;
    private List<MailDnsRecordInfo> missing;

    public MailDnsVerifyResult() {}

    public MailDnsVerifyResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Boolean getVerified() { return verified; }
    public void setVerified(Boolean verified) { this.verified = verified; }

    public List<MailDnsRecordInfo> getMissing() { return missing; }
    public void setMissing(List<MailDnsRecordInfo> missing) { this.missing = missing; }
}
