package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An SSH key stored against the user's account.
 *
 * `fingerprint` is the SHA256 form ssh-keygen prints. It identifies the key material regardless of
 * name or comment, so it is the reliable way to tell whether a key you hold is already stored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsSshKey {
    private Integer id;
    private String name;
    private String type;
    private String publicKey;
    private String fingerprint;
    private Boolean enabled;
    private String createdAt;

    public VpsSshKey() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
