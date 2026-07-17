package com.osir.mcp.models;

import java.util.List;

public class DomainSuggestionsResult {
    private boolean success;
    private String message;
    private List<DomainSuggestion> suggestions;

    public DomainSuggestionsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Getters and setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<DomainSuggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<DomainSuggestion> suggestions) { this.suggestions = suggestions; }
}
