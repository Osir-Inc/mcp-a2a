package com.osir.mcp.models.catalog;

import java.util.List;

public class DedicatedServerCatalogResult {
    private boolean success;
    private String message;
    private List<DedicatedServerConfig> servers;

    public DedicatedServerCatalogResult() {}

    public DedicatedServerCatalogResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public DedicatedServerCatalogResult(boolean success, String message, List<DedicatedServerConfig> servers) {
        this.success = success;
        this.message = message;
        this.servers = servers;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<DedicatedServerConfig> getServers() { return servers; }
    public void setServers(List<DedicatedServerConfig> servers) { this.servers = servers; }
}
