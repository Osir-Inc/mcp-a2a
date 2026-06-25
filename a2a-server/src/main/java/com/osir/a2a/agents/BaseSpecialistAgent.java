package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base class for specialist agents. Provides common utilities
 * for message extraction, domain extraction, scoring, and object mapping.
 */
public abstract class BaseSpecialistAgent implements SpecialistAgent {

    protected static final Pattern DOMAIN_PATTERN = DomainUtils.DOMAIN_PATTERN;

    @Inject
    ObjectMapper objectMapper;

    /** Keywords that give this agent a score boost. Override to customize. */
    protected abstract Set<String> getKeywords();

    /** Skill IDs this agent handles. Used for explicit routing. */
    protected abstract Set<String> getSkillIds();

    /** Keyword score weight. Default 0.25 per match. Override if needed. */
    protected double getKeywordWeight() { return 0.25; }

    @Override
    public double score(A2ATask task) {
        Map<String, Object> metadata = task.getMetadata();
        if (metadata != null) {
            String agent = (String) metadata.get("agent");
            if (agent != null) return getId().equals(agent) ? 1.0 : 0.0;
            String skill = (String) metadata.get("skill");
            if (skill != null) return getSkillIds().contains(skill) ? 1.0 : 0.0;
        }
        String text = getLatestUserMessage(task).toLowerCase();
        double score = 0.0;
        for (String kw : getKeywords()) {
            if (text.contains(kw)) score += getKeywordWeight();
        }
        return Math.min(score, 1.0);
    }

    // --- Shared utilities ---

    protected String getLatestUserMessage(A2ATask task) {
        List<Message> h = task.getHistory();
        for (int i = h.size() - 1; i >= 0; i--) {
            if ("user".equals(h.get(i).getRole())) {
                String text = h.get(i).getTextContent();
                return text != null ? text : "";
            }
        }
        return "";
    }

    protected String extractDomain(String text) {
        return DomainUtils.extractFirst(text);
    }

    protected A2ATask askForDomain(A2ATask task, String action) {
        task.transitionTo(TaskState.INPUT_REQUIRED);
        task.addMessage(new Message("agent", "Please provide a domain name to " + action + " (e.g., example.com)."));
        return task;
    }

    protected A2ATask completeWithResult(A2ATask task, String artifactName, Object result, boolean success, String message) {
        task.addArtifact(Artifact.ofData(artifactName, toMap(result)));
        task.addMessage(new Message("agent", message));
        task.transitionTo(success ? TaskState.COMPLETED : TaskState.FAILED);
        return task;
    }

    protected A2ATask failWithError(A2ATask task, String message) {
        task.transitionTo(TaskState.FAILED);
        task.addMessage(new Message("agent", "Error: " + message));
        return task;
    }

    /**
     * Handle an exception with user-friendly backend error classification.
     */
    protected A2ATask failWithException(A2ATask task, Exception e) {
        return BackendErrorHandler.fail(task, e);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> toMap(Object obj) {
        return objectMapper.convertValue(obj, Map.class);
    }

    protected String getSkillFromMetadata(A2ATask task) {
        if (task == null) return null;
        Map<String, Object> metadata = task.getMetadata();
        return metadata != null ? (String) metadata.get("skill") : null;
    }

    protected A2ATask askForInput(A2ATask task, String message) {
        task.transitionTo(TaskState.INPUT_REQUIRED);
        task.addMessage(new Message("agent", message));
        return task;
    }

    protected String meta(A2ATask task, String key) {
        Map<String, Object> md = task.getMetadata();
        if (md == null) return null;
        Object v = md.get(key);
        return v != null ? v.toString() : null;
    }

    protected Integer metaInt(A2ATask task, String key) {
        String v = meta(task, key);
        if (v == null) return null;
        try { return Integer.parseInt(v); } catch (NumberFormatException ignored) { return null; }
    }

    protected Double metaDouble(A2ATask task, String key) {
        String v = meta(task, key);
        if (v == null) return null;
        try { return Double.parseDouble(v); } catch (NumberFormatException ignored) { return null; }
    }
}
