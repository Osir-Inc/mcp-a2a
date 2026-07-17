package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsOsTemplateListApiResponse {
    private List<VpsOsTemplate> templates;

    public List<VpsOsTemplate> getTemplates() { return templates; }
    public void setTemplates(List<VpsOsTemplate> templates) { this.templates = templates; }
}
