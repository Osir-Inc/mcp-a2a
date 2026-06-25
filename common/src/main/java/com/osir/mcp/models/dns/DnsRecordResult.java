package com.osir.mcp.models.dns;

public class DnsRecordResult {
    private boolean success;
    private String message;
    private DnsRecord record;

    public DnsRecordResult() {}

    public DnsRecordResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public DnsRecord getRecord() { return record; }
    public void setRecord(DnsRecord record) { this.record = record; }
}
