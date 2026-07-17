package com.osir.a2a.protocol;

import java.util.Map;

/**
 * Typed DTO for tasks/send JSON-RPC params.
 * Replaces manual Map parsing in A2AResource.
 */
public class TaskSendParams {

    private String id;
    private Message message;
    private String skill;
    private String agent;
    private String webhookUrl;
    private String refreshToken;
    private Map<String, Object> metadata;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Message getMessage() { return message; }
    public void setMessage(Message message) { this.message = message; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
