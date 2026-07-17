package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// API shape: { locations: [VpsLocation], count }
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsLocationListApiResponse {
    private List<VpsLocation> locations;
    private int count;

    public List<VpsLocation> getLocations() { return locations; }
    public void setLocations(List<VpsLocation> locations) { this.locations = locations; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}
