package com.osir.mcp.models.host;

import java.util.List;

public class HostCreateRequest {
    private String hostname;
    private List<String> ipAddresses;

    public HostCreateRequest() {}

    public HostCreateRequest(String hostname, List<String> ipAddresses) {
        this.hostname = hostname;
        this.ipAddresses = ipAddresses;
    }

    // Getters and Setters
    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public List<String> getIpAddresses() { return ipAddresses; }
    public void setIpAddresses(List<String> ipAddresses) { this.ipAddresses = ipAddresses; }
}
