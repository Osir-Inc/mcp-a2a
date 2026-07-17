package com.osir.mcp.models.vps;

/**
 * Outcome of queueing an OS install.
 *
 * The build is asynchronous: buildState is QUEUED on return, and the caller polls
 * getVpsInstanceDetails until it reads COMPLETE (or FAILED).
 */
public class VpsBuildResult {
    private boolean success;
    private String message;
    private String instanceId;
    /** UNBUILT | QUEUED | BUILDING | COMPLETE | FAILED */
    private String buildState;
    private String built;
    private boolean buildFailed;
    private Integer osTemplateId;
    private String osTemplateName;

    public VpsBuildResult() {}

    public VpsBuildResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

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
