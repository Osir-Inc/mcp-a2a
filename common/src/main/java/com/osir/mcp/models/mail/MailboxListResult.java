package com.osir.mcp.models.mail;

import java.util.List;

public class MailboxListResult {
    private boolean success;
    private String message;
    private List<MailboxSummary> mailboxes;
    private int totalCount;

    public MailboxListResult() {}

    public MailboxListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<MailboxSummary> getMailboxes() { return mailboxes; }
    public void setMailboxes(List<MailboxSummary> mailboxes) { this.mailboxes = mailboxes; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
}
