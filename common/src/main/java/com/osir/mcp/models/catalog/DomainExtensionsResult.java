package com.osir.mcp.models.catalog;

import java.util.List;

public class DomainExtensionsResult {
    private boolean success;
    private String message;
    private List<DomainExtension> extensions;

    public DomainExtensionsResult() {}

    public DomainExtensionsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DomainExtensionsResult(boolean success, String message, List<DomainExtension> extensions) {
        this.success = success;
        this.message = message;
        this.extensions = extensions;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<DomainExtension> getExtensions() { return extensions; }
    public void setExtensions(List<DomainExtension> extensions) { this.extensions = extensions; }
}
