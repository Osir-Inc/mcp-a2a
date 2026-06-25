package com.osir.a2a.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class TaskRepository {

    @Inject
    EntityManager em;

    @Transactional
    public void save(TaskEntity entity) {
        entity.setUpdatedAt(Instant.now());
        em.merge(entity);
    }

    public Optional<TaskEntity> findById(String taskId) {
        return Optional.ofNullable(em.find(TaskEntity.class, taskId));
    }

    /**
     * Delete terminal tasks older than cutoff.
     */
    @Transactional
    public int cleanupTerminal(Instant cutoff) {
        return em.createQuery(
                "DELETE FROM TaskEntity t WHERE t.updatedAt < :cutoff AND t.status IN ('completed', 'failed', 'canceled')")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    /**
     * Delete non-terminal (stuck) tasks older than cutoff.
     * These are tasks that never completed — stuck in submitted, working, or input-required.
     */
    @Transactional
    public int cleanupStuck(Instant cutoff) {
        return em.createQuery(
                "DELETE FROM TaskEntity t WHERE t.updatedAt < :cutoff AND t.status NOT IN ('completed', 'failed', 'canceled')")
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }
}
