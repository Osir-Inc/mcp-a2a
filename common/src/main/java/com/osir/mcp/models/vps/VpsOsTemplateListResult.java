package com.osir.mcp.models.vps;

import java.util.List;

public class VpsOsTemplateListResult {
    private boolean success;
    private String message;
    private List<VpsOsTemplate> templates;

    public VpsOsTemplateListResult() {}

    public VpsOsTemplateListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<VpsOsTemplate> getTemplates() { return templates; }
    public void setTemplates(List<VpsOsTemplate> templates) { this.templates = templates; }
}
