package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// API shape: { extensions: [DomainExtension], totalExtensions, registrars }
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainExtensionsApiResponse {
    private List<DomainExtension> extensions;
    private int totalExtensions;

    public List<DomainExtension> getExtensions() { return extensions; }
    public void setExtensions(List<DomainExtension> extensions) { this.extensions = extensions; }

    public int getTotalExtensions() { return totalExtensions; }
    public void setTotalExtensions(int totalExtensions) { this.totalExtensions = totalExtensions; }
}
