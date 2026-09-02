package com.osir.mcp.models.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * GET /v1/public/catalog/bundle?domain={d} (anonymous) — per-domain hosting offer.
 * Passed through to the model verbatim; the nested options (vps.recommended, mail.packages,
 * webForwarding, appDeploy) are display data the LLM reads, so no typed mapping needed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class HostingBundleResponse {

    private String domain;
    private Map<String, Object> options;
    private List<String> nextSteps;

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }
    public List<String> getNextSteps() { return nextSteps; }
    public void setNextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; }
}
