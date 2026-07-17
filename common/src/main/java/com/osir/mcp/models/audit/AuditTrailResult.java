package com.osir.mcp.models.audit;

import java.util.List;

public class AuditTrailResult {
    private boolean success;
    private String message;
    private String domain;
    private List<AuditEntry> entries;

    public AuditTrailResult() {}

    public AuditTrailResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public List<AuditEntry> getEntries() { return entries; }
    public void setEntries(List<AuditEntry> entries) { this.entries = entries; }
}
