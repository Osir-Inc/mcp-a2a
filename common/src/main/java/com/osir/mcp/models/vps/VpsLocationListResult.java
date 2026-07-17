package com.osir.mcp.models.vps;

import java.util.List;

public class VpsLocationListResult {
    private boolean success;
    private String message;
    private List<VpsLocation> locations;

    public VpsLocationListResult() {}

    public VpsLocationListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<VpsLocation> getLocations() { return locations; }
    public void setLocations(List<VpsLocation> locations) { this.locations = locations; }
}
