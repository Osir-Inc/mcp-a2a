package com.osir.mcp.models.suggestion;

import java.util.List;

public class DomainSuggestionResponse {
    private List<DomainSuggestionResult> results;

    public DomainSuggestionResponse() {}

    public DomainSuggestionResponse(List<DomainSuggestionResult> results) {
        this.results = results;
    }

    // Getters and Setters
    public List<DomainSuggestionResult> getResults() { return results; }
    public void setResults(List<DomainSuggestionResult> results) { this.results = results; }

    @Override
    public String toString() {
        return "DomainSuggestionResponse{" +
                "results=" + results +
                '}';
    }
}