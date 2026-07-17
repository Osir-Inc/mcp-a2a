package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// API shape: { packages: [VpsPackageSummary], count }
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsPackageListApiResponse {
    private List<VpsPackageSummary> packages;
    private int count;

    public List<VpsPackageSummary> getPackages() { return packages; }
    public void setPackages(List<VpsPackageSummary> packages) { this.packages = packages; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
