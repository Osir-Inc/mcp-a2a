package com.osir.mcp.models.suggestion;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

public class BulkDomainSuggestionsResult {
    private boolean success;
    private String message;
    private List<KeywordGroup> groups;
    private List<String> requestedTlds;
    private List<String> returnedTlds;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> errors;

    public BulkDomainSuggestionsResult() {}

    public BulkDomainSuggestionsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<KeywordGroup> getGroups() { return groups; }
    public void setGroups(List<KeywordGroup> groups) { this.groups = groups; }

    public List<String> getRequestedTlds() { return requestedTlds; }
    public void setRequestedTlds(List<String> requestedTlds) { this.requestedTlds = requestedTlds; }

    public List<String> getReturnedTlds() { return returnedTlds; }
    public void setReturnedTlds(List<String> returnedTlds) { this.returnedTlds = returnedTlds; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
