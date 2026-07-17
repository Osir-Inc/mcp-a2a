package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsSshKeyListApiResponse {
    private List<VpsSshKey> keys;

    public List<VpsSshKey> getKeys() { return keys; }
    public void setKeys(List<VpsSshKey> keys) { this.keys = keys; }
}
