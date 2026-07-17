package com.osir.mcp.models.vps;

public class VpsSshKeyCreateRequest {
    private String name;
    private String publicKey;

    public VpsSshKeyCreateRequest() {}

    public VpsSshKeyCreateRequest(String name, String publicKey) {
        this.name = name;
        this.publicKey = publicKey;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
}
