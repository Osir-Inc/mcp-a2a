package com.osir.mcp.models.suggestion;

import java.util.List;

public class BulkSuggestionResponse {
    private List<BulkSuggestionItem> suggestions;
    private List<BulkSuggestionError> errors;

    public static class BulkSuggestionItem {
        private String originalName;
        private List<DomainSuggestionResult> suggestions;

        public BulkSuggestionItem() {}

        public BulkSuggestionItem(String originalName, List<DomainSuggestionResult> suggestions) {
            this.originalName = originalName;
            this.suggestions = suggestions;
        }

        // Getters and Setters
        public String getOriginalName() { return originalName; }
        public void setOriginalName(String originalName) { this.originalName = originalName; }

        public List<DomainSuggestionResult> getSuggestions() { return suggestions; }
        public void setSuggestions(List<DomainSuggestionResult> suggestions) { this.suggestions = suggestions; }
    }

    public static class BulkSuggestionError {
        private String originalName;
        private String errorMessage;

        public BulkSuggestionError() {}

        public BulkSuggestionError(String originalName, String errorMessage) {
            this.originalName = originalName;
            this.errorMessage = errorMessage;
        }

        // Getters and Setters
        public String getOriginalName() { return originalName; }
        public void setOriginalName(String originalName) { this.originalName = originalName; }

        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }

    public BulkSuggestionResponse() {}

    public BulkSuggestionResponse(List<BulkSuggestionItem> suggestions, List<BulkSuggestionError> errors) {
        this.suggestions = suggestions;
        this.errors = errors;
    }

    // Getters and Setters
    public List<BulkSuggestionItem> getSuggestions() { return suggestions; }
    public void setSuggestions(List<BulkSuggestionItem> suggestions) { this.suggestions = suggestions; }

    public List<BulkSuggestionError> getErrors() { return errors; }
    public void setErrors(List<BulkSuggestionError> errors) { this.errors = errors; }
}