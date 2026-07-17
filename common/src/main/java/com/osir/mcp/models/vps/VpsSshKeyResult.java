package com.osir.mcp.models.vps;

public class VpsSshKeyResult {
    private boolean success;
    private String message;
    private VpsSshKey key;
    /** False when the account already had this key material, so nothing new was stored. */
    private boolean created;

    public VpsSshKeyResult() {}

    public VpsSshKeyResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public VpsSshKey getKey() { return key; }
    public void setKey(VpsSshKey key) { this.key = key; }

    public boolean isCreated() { return created; }
    public void setCreated(boolean created) { this.created = created; }
}
