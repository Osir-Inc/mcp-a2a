package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class CategorizedTldsResult {
    private boolean success;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer totalCandidates;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private CategorizedTldsFilters filtersApplied;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<CategorizedTldCandidate> candidates;

    public CategorizedTldsResult() {}

    public CategorizedTldsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public CategorizedTldsResult(boolean success, int totalCandidates, CategorizedTldsFilters filtersApplied,
                                   List<CategorizedTldCandidate> candidates, String message) {
        this.success = success;
        this.totalCandidates = totalCandidates;
        this.filtersApplied = filtersApplied;
        this.candidates = candidates;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getTotalCandidates() { return totalCandidates; }
    public void setTotalCandidates(Integer totalCandidates) { this.totalCandidates = totalCandidates; }

    public CategorizedTldsFilters getFiltersApplied() { return filtersApplied; }
    public void setFiltersApplied(CategorizedTldsFilters filtersApplied) { this.filtersApplied = filtersApplied; }

    public List<CategorizedTldCandidate> getCandidates() { return candidates; }
    public void setCandidates(List<CategorizedTldCandidate> candidates) { this.candidates = candidates; }
}
