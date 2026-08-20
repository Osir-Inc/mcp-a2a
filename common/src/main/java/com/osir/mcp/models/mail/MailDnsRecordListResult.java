package com.osir.mcp.models.mail;

import java.util.List;

public class MailDnsRecordListResult {
    private boolean success;
    private String message;
    private List<MailDnsRecordInfo> records;

    public MailDnsRecordListResult() {}

    public MailDnsRecordListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<MailDnsRecordInfo> getRecords() { return records; }
    public void setRecords(List<MailDnsRecordInfo> records) { this.records = records; }
}
