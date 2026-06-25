package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductCatalogResponse {
    private DomainExtensionsApiResponse domains;
    private List<Object> vpsPackages;
    private List<DedicatedServerConfig> dedicatedServers;

    public ProductCatalogResponse() {}

    // Getters and Setters
    public DomainExtensionsApiResponse getDomains() { return domains; }
    public void setDomains(DomainExtensionsApiResponse domains) { this.domains = domains; }

    public List<Object> getVpsPackages() { return vpsPackages; }
    public void setVpsPackages(List<Object> vpsPackages) { this.vpsPackages = vpsPackages; }

    public List<DedicatedServerConfig> getDedicatedServers() { return dedicatedServers; }
    public void setDedicatedServers(List<DedicatedServerConfig> dedicatedServers) { this.dedicatedServers = dedicatedServers; }
}
