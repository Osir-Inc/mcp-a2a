package com.osir.a2a.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.persistence.TaskEntity;
import com.osir.a2a.persistence.TaskRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task store with in-memory cache backed by JPA persistence.
 * Reads hit the cache first, writes go to both cache and database.
 */
@ApplicationScoped
public class TaskStore {

    private static final Logger LOG = Logger.getLogger(TaskStore.class);
    private static final long TERMINAL_TTL_SECONDS = 3600;       // 1 hour for completed/failed/canceled
    private static final long NON_TERMINAL_TTL_SECONDS = 86400;  // 24 hours for stuck tasks

    private final Map<String, A2ATask> cache = new ConcurrentHashMap<>();

    @Inject TaskRepository repository;
    @Inject ObjectMapper objectMapper;

    public A2ATask create(String taskId, Message initialMessage) {
        A2ATask task = new A2ATask(taskId, initialMessage);
        cache.put(taskId, task);
        persist(task, null, null, null);
        return task;
    }

    public Optional<A2ATask> get(String taskId) {
        A2ATask cached = cache.get(taskId);
        if (cached != null) return Optional.of(cached);

        // Try database — use putIfAbsent to avoid overwriting a concurrent update
        return repository.findById(taskId).flatMap(entity -> {
            try {
                A2ATask task = objectMapper.readValue(entity.getTaskJson(), A2ATask.class);
                cache.putIfAbsent(taskId, task);
                return Optional.of(cache.get(taskId));
            } catch (Exception e) {
                LOG.warnf("Failed to deserialize persisted task %s: %s", taskId, e.getMessage());
                return Optional.empty();
            }
        });
    }

    public A2ATask update(A2ATask task) {
        cache.put(task.getId(), task);
        persist(task, null, null, null);
        return task;
    }

    public A2ATask update(A2ATask task, String agentId, String skill, String user) {
        cache.put(task.getId(), task);
        persist(task, agentId, skill, user);
        return task;
    }

    public boolean cancel(String taskId) {
        A2ATask task = cache.get(taskId);
        if (task == null) {
            // Try loading from DB
            var opt = get(taskId);
            if (opt.isEmpty()) return false;
            task = opt.get();
        }
        if (task.isTerminal()) return false;
        task.transitionTo(TaskState.CANCELED);
        persist(task, null, null, null);
        return true;
    }

    public int size() {
        return cache.size();
    }

    @Scheduled(every = "5m")
    void cleanupExpired() {
        Instant terminalCutoff = Instant.now().minusSeconds(TERMINAL_TTL_SECONDS);
        Instant stuckCutoff = Instant.now().minusSeconds(NON_TERMINAL_TTL_SECONDS);

        // Clean cache: terminal tasks after 1h, non-terminal (stuck) after 24h
        int before = cache.size();
        cache.entrySet().removeIf(entry -> {
            A2ATask t = entry.getValue();
            if (t.isTerminal()) return t.getUpdatedAt().isBefore(terminalCutoff);
            return t.getUpdatedAt().isBefore(stuckCutoff);
        });
        int cacheRemoved = before - cache.size();

        // Clean database — both terminal and stuck tasks
        int dbRemoved = 0;
        try {
            dbRemoved += repository.cleanupTerminal(terminalCutoff);
            dbRemoved += repository.cleanupStuck(stuckCutoff);
        } catch (Exception e) {
            LOG.warnf("DB cleanup failed: %s", e.getMessage());
        }

        if (cacheRemoved > 0 || dbRemoved > 0) {
            LOG.infof("Task cleanup: cache=%d removed, db=%d removed, cache=%d remaining",
                    cacheRemoved, dbRemoved, cache.size());
        }
    }

    private void persist(A2ATask task, String agentId, String skill, String user) {
        try {
            String json = objectMapper.writeValueAsString(task);
            TaskEntity entity = new TaskEntity(task.getId(), task.getStatus().getValue(), json);
            entity.setAgentId(agentId);
            entity.setSkill(skill);
            entity.setUserIdentity(user);
            repository.save(entity);
        } catch (Exception e) {
            LOG.warnf("Failed to persist task %s: %s", task.getId(), e.getMessage());
        }
    }
}
