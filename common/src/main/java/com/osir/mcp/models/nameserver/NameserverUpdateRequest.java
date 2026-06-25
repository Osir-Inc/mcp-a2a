package com.osir.mcp.models.nameserver;

import java.util.List;

// Nameserver Update Models
public class NameserverUpdateRequest {
    private List<String> nameservers;
    private String environment;
    private boolean replaceAll;

    public NameserverUpdateRequest() {}

    public NameserverUpdateRequest(List<String> nameservers, String environment, boolean replaceAll) {
        this.nameservers = nameservers;
        this.environment = environment;
        this.replaceAll = replaceAll;
    }

    // Getters and Setters
    public List<String> getNameservers() { return nameservers; }
    public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public boolean isReplaceAll() { return replaceAll; }
    public void setReplaceAll(boolean replaceAll) { this.replaceAll = replaceAll; }
}
