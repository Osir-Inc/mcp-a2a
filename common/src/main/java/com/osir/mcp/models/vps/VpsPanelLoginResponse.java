package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsPanelLoginResponse {
    private String instanceId;
    private String hostname;
    private String loginUrl;
    private String expiresAt;
    private String message;

    public VpsPanelLoginResponse() {}

    public boolean isSuccess() { return loginUrl != null; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getLoginUrl() { return loginUrl; }
    public void setLoginUrl(String loginUrl) { this.loginUrl = loginUrl; }

    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
