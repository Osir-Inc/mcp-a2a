package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An installable OS template.
 *
 * NOTE: ids are per-VirtFusion-install and change when templates are re-imported, so they must always
 * be resolved at runtime via listVpsOsTemplates. Never hardcode one.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsOsTemplate {
    private Integer id;
    private String name;
    private String version;
    private String variant;
    private Integer arch;
    private String description;
    private boolean eol;
    private String eolDate;
    private Integer deployType;
    private String type;

    public VpsOsTemplate() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public Integer getArch() { return arch; }
    public void setArch(Integer arch) { this.arch = arch; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isEol() { return eol; }
    public void setEol(boolean eol) { this.eol = eol; }

    public String getEolDate() { return eolDate; }
    public void setEolDate(String eolDate) { this.eolDate = eolDate; }

    public Integer getDeployType() { return deployType; }
    public void setDeployType(Integer deployType) { this.deployType = deployType; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    /** e.g. "Debian 12 (Bookworm) Minimal" - what to show a user choosing an OS. */
    public String getDisplayName() {
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (version != null && !version.isEmpty()) sb.append(' ').append(version);
        if (variant != null && !variant.isEmpty()) sb.append(' ').append(variant);
        return sb.toString().trim();
    }
}
