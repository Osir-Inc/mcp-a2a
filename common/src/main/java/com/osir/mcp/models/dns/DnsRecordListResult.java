package com.osir.mcp.models.dns;

import java.util.List;

public class DnsRecordListResult {
    private boolean success;
    private String message;
    private String domain;
    private List<DnsRecord> records;

    public DnsRecordListResult() {}

    public DnsRecordListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public List<DnsRecord> getRecords() { return records; }
    public void setRecords(List<DnsRecord> records) { this.records = records; }
}
