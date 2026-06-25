package com.osir.mcp.models.suggestion;

import java.util.List;

public class DomainSuggestionsResult {
    private boolean success;
    private String message;
    private List<DomainSuggestionResult> suggestions;

    public DomainSuggestionsResult() {}

    public DomainSuggestionsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DomainSuggestionsResult(boolean success, String message, List<DomainSuggestionResult> suggestions) {
        this.success = success;
        this.message = message;
        this.suggestions = suggestions;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<DomainSuggestionResult> getSuggestions() { return suggestions; }
    public void setSuggestions(List<DomainSuggestionResult> suggestions) { this.suggestions = suggestions; }

    @Override
    public String toString() {
        return "DomainSuggestionsResult{" +
                "success=" + success +
                ", message='" + message + '\'' +
                ", suggestions=" + suggestions +
                '}';
    }
}