package com.osir.a2a.resources;

import com.osir.a2a.protocol.A2ATask;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Structured audit logger for A2A operations.
 * Logs user identity, agent, skill, and outcome for every task execution.
 */
@ApplicationScoped
public class AuditLogger {

    private static final Logger AUDIT = Logger.getLogger("AUDIT");

    public void logTaskSubmitted(String taskId, String user, String skill, String agent, String messagePreview) {
        AUDIT.infof("TASK_SUBMITTED taskId=%s user=%s skill=%s agent=%s message=\"%s\"",
                taskId, safe(user), safe(skill), safe(agent), truncate(messagePreview, 200));
    }

    public void logTaskCompleted(String taskId, String agentId, String status, long durationMs) {
        AUDIT.infof("TASK_COMPLETED taskId=%s agent=%s status=%s duration=%dms",
                taskId, safe(agentId), status, durationMs);
    }

    public void logTaskFailed(String taskId, String agentId, String reason) {
        AUDIT.warnf("TASK_FAILED taskId=%s agent=%s reason=\"%s\"",
                taskId, safe(agentId), truncate(reason, 200));
    }

    private String safe(String value) {
        return value != null ? value : "-";
    }

    private String truncate(String value, int maxLen) {
        if (value == null) return "-";
        return value.length() > maxLen ? value.substring(0, maxLen) + "..." : value;
    }
}
