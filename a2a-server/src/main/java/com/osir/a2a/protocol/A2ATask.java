package com.osir.a2a.protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A2A Task - the core unit of work in the A2A protocol.
 * Thread-safe: all list mutations are synchronized.
 */
public class A2ATask {

    private String id;
    private volatile TaskState status;
    private final List<Message> history = Collections.synchronizedList(new ArrayList<>());
    private final List<Artifact> artifacts = Collections.synchronizedList(new ArrayList<>());
    private volatile Map<String, Object> metadata;
    private final Instant createdAt;
    private volatile Instant updatedAt;

    public A2ATask() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public A2ATask(String id, Message initialMessage) {
        this();
        this.id = id;
        this.status = TaskState.SUBMITTED;
        this.history.add(initialMessage);
    }

    public void addMessage(Message message) {
        this.history.add(message);
        this.updatedAt = Instant.now();
    }

    public void addArtifact(Artifact artifact) {
        this.artifacts.add(artifact);
        this.updatedAt = Instant.now();
    }

    public void transitionTo(TaskState newState) {
        this.status = newState;
        this.updatedAt = Instant.now();
    }

    public boolean isTerminal() {
        return status == TaskState.COMPLETED
                || status == TaskState.FAILED
                || status == TaskState.CANCELED;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public TaskState getStatus() { return status; }
    public void setStatus(TaskState status) { this.status = status; }
    public List<Message> getHistory() { return history; }
    public List<Artifact> getArtifacts() { return artifacts; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
