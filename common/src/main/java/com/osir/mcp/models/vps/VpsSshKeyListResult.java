package com.osir.mcp.models.vps;

import java.util.List;

public class VpsSshKeyListResult {
    private boolean success;
    private String message;
    private List<VpsSshKey> keys;

    public VpsSshKeyListResult() {}

    public VpsSshKeyListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<VpsSshKey> getKeys() { return keys; }
    public void setKeys(List<VpsSshKey> keys) { this.keys = keys; }
}
