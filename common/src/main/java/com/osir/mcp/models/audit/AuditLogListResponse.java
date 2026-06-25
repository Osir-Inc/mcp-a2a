package com.osir.mcp.models.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

// API shape: { page, pageSize, total, data: [AuditEntry] }
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditLogListResponse {
    private int page;
    @JsonProperty("pageSize")
    private int size;
    @JsonProperty("total")
    private int totalCount;
    @JsonProperty("data")
    private List<AuditEntry> entries;

    public AuditLogListResponse() {}

    public List<AuditEntry> getEntries() { return entries; }
    public void setEntries(List<AuditEntry> entries) { this.entries = entries; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
