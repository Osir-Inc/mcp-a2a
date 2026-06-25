package com.osir.mcp.models.audit;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEntry {
    private String action;
    private String actor;
    private String actorType;
    private String domain;
    private String errorMessage;
    private String details;

    @JsonProperty("createdAt")
    private String timestamp;

    // API field is "success" (nullable Boolean); primitive boolean would default to false
    @JsonProperty("success")
    private Boolean wasSuccessful;

    public AuditEntry() {}

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getActorType() { return actorType; }
    public void setActorType(String actorType) { this.actorType = actorType; }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public Boolean isWasSuccessful() { return wasSuccessful; }
    public void setWasSuccessful(Boolean wasSuccessful) { this.wasSuccessful = wasSuccessful; }
}
