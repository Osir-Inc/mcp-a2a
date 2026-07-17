package com.osir.mcp.models.audit;

import java.util.List;

public class RecentActivityResult {
    private boolean success;
    private String message;
    private List<AuditEntry> activities;

    public RecentActivityResult() {}

    public RecentActivityResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<AuditEntry> getActivities() { return activities; }
    public void setActivities(List<AuditEntry> activities) { this.activities = activities; }
}
