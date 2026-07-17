package com.osir.mcp.models.host;

import java.util.List;

public class HostRecord {
    private String hostname;
    private List<String> ipAddresses;
    private String domain;
    private String createdAt;

    public HostRecord() {}

    public HostRecord(String hostname, List<String> ipAddresses, String domain, String createdAt) {
        this.hostname = hostname;
        this.ipAddresses = ipAddresses;
        this.domain = domain;
        this.createdAt = createdAt;
    }

    // Getters and Setters
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public List<String> getIpAddresses() { return ipAddresses; }
    public void setIpAddresses(List<String> ipAddresses) { this.ipAddresses = ipAddresses; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
