package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The backend's build-status shape, returned by POST /build and embedded in instance details. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsBuildResponse {
    private String instanceId;
    private String buildState;
    private String built;
    private boolean buildFailed;
    private Integer osTemplateId;
    private String osTemplateName;

    public String getInstanceId() { return instanceId; }
    public void setInstanceId(String instanceId) { this.instanceId = instanceId; }

    public String getBuildState() { return buildState; }
    public void setBuildState(String buildState) { this.buildState = buildState; }

    public String getBuilt() { return built; }
    public void setBuilt(String built) { this.built = built; }

    public boolean isBuildFailed() { return buildFailed; }
    public void setBuildFailed(boolean buildFailed) { this.buildFailed = buildFailed; }

    public Integer getOsTemplateId() { return osTemplateId; }
    public void setOsTemplateId(Integer osTemplateId) { this.osTemplateId = osTemplateId; }

    public String getOsTemplateName() { return osTemplateName; }
    public void setOsTemplateName(String osTemplateName) { this.osTemplateName = osTemplateName; }
}
