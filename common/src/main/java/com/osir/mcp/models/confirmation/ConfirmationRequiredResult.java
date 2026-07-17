package com.osir.mcp.models.confirmation;

public class ConfirmationRequiredResult {
    private String actionId;
    private String toolName;
    private String summary;
    private String expiresIn;
    private String instruction;

    public ConfirmationRequiredResult() {}

    public ConfirmationRequiredResult(String actionId, String toolName, String summary) {
        this.actionId = actionId;
        this.toolName = toolName;
        this.summary = summary;
        this.instruction = "Show this summary to the user and ask for approval. If approved, call executeConfirmedAction with the actionId above. If declined, discard — the action will expire automatically.";
    }

    public String getActionId() { return actionId; }
    public void setActionId(String actionId) { this.actionId = actionId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getExpiresIn() { return expiresIn; }
    public void setExpiresIn(String expiresIn) { this.expiresIn = expiresIn; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
