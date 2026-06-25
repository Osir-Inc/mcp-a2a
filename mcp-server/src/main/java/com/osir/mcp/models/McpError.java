package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class McpError {

    private final boolean success = false;
    private final String errorCode;
    private final String message;
    private final Object data = null;

    public McpError(String errorCode, String message) {
        this.errorCode = errorCode;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public String getErrorCode() { return errorCode; }
    public String getMessage() { return message; }
    public Object getData() { return data; }
}
