package com.osir.mcp.models.mail;

import java.util.Map;

public class MailUsageResult {
    private boolean success;
    private String message;
    /** emailAddress -> used bytes. */
    private Map<String, Long> usage;

    public MailUsageResult() {}

    public MailUsageResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Map<String, Long> getUsage() { return usage; }
    public void setUsage(Map<String, Long> usage) { this.usage = usage; }
}
