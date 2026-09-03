package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * POST /v2/domains/{domain}/lock|unlock - standard v2 envelope, payload nested under data
 * (verified against backend v2.11.5, docs/agent-readiness/BACKEND-CONTRACT-lock-and-info.md).
 * Failures are HTTP 400 with {success:false, error, errorCode} and surface as exceptions.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainLockResponse {

    private boolean success;
    private Data data;
    private String timestamp;
    private String error;
    private String errorCode;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String domain;
        private Boolean locked;
        private String message;

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }
        public Boolean getLocked() { return locked; }
        public void setLocked(Boolean locked) { this.locked = locked; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    public DomainLockResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
