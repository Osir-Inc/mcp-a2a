package com.osir.mcp.models.auth;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * While the login is pending, {@code sessionKey}, {@code tokenType} and {@code expiresIn} have no
 * value yet. Serialising them as null made the whole result fail the tool's generated output schema
 * ("Structured content does not match the tool's output schema"): the schema types them string and
 * integer, and nothing in it is marked required, so omitting them validates while null does not. A
 * polling agent could therefore not tell "not approved yet" from "broken", and either gave up or
 * hammered the endpoint. {@code status} ("pending", RFC 8628's authorization_pending) is what the
 * caller branches on.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceLoginStatusResult {
    private boolean success;
    private String message;
    private String status; // pending, complete, expired, denied, slow_down
    private Long expiresIn;
    private String tokenType;
    private String sessionKey;

    public DeviceLoginStatusResult(boolean success, String message, String status) {
        this.success = success;
        this.message = message;
        this.status = status;
    }

    public DeviceLoginStatusResult(boolean success, String message, String status,
                                   Long expiresIn, String tokenType) {
        this.success = success;
        this.message = message;
        this.status = status;
        this.expiresIn = expiresIn;
        this.tokenType = tokenType;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Long expiresIn) { this.expiresIn = expiresIn; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }
}
