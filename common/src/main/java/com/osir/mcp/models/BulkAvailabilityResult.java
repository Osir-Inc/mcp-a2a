package com.osir.mcp.models;

import java.util.List;

public class BulkAvailabilityResult {
    private boolean success;
    private String message;
    private List<DomainAvailabilityResult> results;

    public BulkAvailabilityResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<DomainAvailabilityResult> getResults() { return results; }
    public void setResults(List<DomainAvailabilityResult> results) { this.results = results; }
}
