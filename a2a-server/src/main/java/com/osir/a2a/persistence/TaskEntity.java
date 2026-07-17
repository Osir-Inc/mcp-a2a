package com.osir.a2a.persistence;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * JPA entity for persisted A2A tasks.
 * Stores the full task state as JSON for flexibility.
 */
@Entity
@Table(name = "a2a_tasks")
public class TaskEntity {

    @Id
    @Column(length = 64)
    private String taskId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String taskJson;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 64)
    private String agentId;

    @Column(length = 64)
    private String skill;

    @Column(length = 128)
    private String userIdentity;

    public TaskEntity() {}

    public TaskEntity(String taskId, String status, String taskJson) {
        this.taskId = taskId;
        this.status = status;
        this.taskJson = taskJson;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTaskJson() { return taskJson; }
    public void setTaskJson(String taskJson) { this.taskJson = taskJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getAgentId() { return agentId; }
    public void setAgentId(String agentId) { this.agentId = agentId; }
    public String getSkill() { return skill; }
    public void setSkill(String skill) { this.skill = skill; }
    public String getUserIdentity() { return userIdentity; }
    public void setUserIdentity(String userIdentity) { this.userIdentity = userIdentity; }
}
