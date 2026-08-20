package com.osir.mcp.models.mail;

import java.util.List;

public class MailPlanListResult {
    private boolean success;
    private String message;
    private List<MailPlan> plans;

    public MailPlanListResult() {}

    public MailPlanListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<MailPlan> getPlans() { return plans; }
    public void setPlans(List<MailPlan> plans) { this.plans = plans; }
}
