package com.osir.mcp.models.design;

import java.util.Map;

/**
 * Result of osirSiteDesignBrief. {@code systemPrompt} is the fully resolved design prompt the calling
 * LLM should follow to write the HTML; {@code editRules} governs later revisions; {@code brief} is
 * the normalized brief echoed back so the LLM can re-submit it after changes.
 */
public record DesignBriefResult(boolean success, String message, String systemPrompt, String editRules,
                                Map<String, Object> brief) {
    public static DesignBriefResult fail(String msg) {
        return new DesignBriefResult(false, msg, null, null, null);
    }
}
