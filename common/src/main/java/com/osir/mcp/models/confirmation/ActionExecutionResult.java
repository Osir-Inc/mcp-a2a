package com.osir.mcp.models.confirmation;

import com.fasterxml.jackson.annotation.JsonInclude;

public class ActionExecutionResult {
    private boolean success;
    private String message;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Object result;

    public ActionExecutionResult() {}

    public ActionExecutionResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ActionExecutionResult(boolean success, String message, Object result) {
        this.success = success;
        this.message = message;
        this.result = result;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
}
