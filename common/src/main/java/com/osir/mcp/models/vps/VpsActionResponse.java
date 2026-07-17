package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsActionResponse {
    private String message;
    private String instanceId;
    private String status;

    public VpsActionResponse() {}

    public boolean isSuccess() { return message != null; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
