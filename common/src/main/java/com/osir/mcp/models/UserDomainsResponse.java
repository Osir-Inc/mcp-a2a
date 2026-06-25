package com.osir.mcp.models;

public class UserDomainsResponse {
    private boolean success;
    private String message;
    private java.util.List<DomainSummary> domains;
    private int totalCount;
    private int page;
    private int size;

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public java.util.List<DomainSummary> getDomains() { return domains; }
    public void setDomains(java.util.List<DomainSummary> domains) { this.domains = domains; }
    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
}
