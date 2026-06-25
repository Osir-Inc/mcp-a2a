package com.osir.mcp.models;

import java.util.List;

public class ValidationResult {
    private boolean valid;
    private String message;
    private List<String> issues;

    public ValidationResult(boolean valid, String message) {
        this.valid = valid;
        this.message = message;
    }

    // Getters and setters
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<String> getIssues() { return issues; }
    public void setIssues(List<String> issues) { this.issues = issues; }
}
