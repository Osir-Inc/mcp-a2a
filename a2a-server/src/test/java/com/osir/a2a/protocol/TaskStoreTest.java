package com.osir.a2a.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import static org.junit.jupiter.api.Assertions.*;

class TaskStoreTest {

    @Mock TaskRepository repository;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    TaskStore store;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void create_and_get() {
        A2ATask task = store.create("t1", new Message("user", "hello"));
        assertEquals("t1", task.getId());
        assertEquals(TaskState.SUBMITTED, task.getStatus());

        assertTrue(store.get("t1").isPresent());
        assertFalse(store.get("nonexistent").isPresent());
    }

    @Test
    void cancel_working_task() {
        A2ATask task = store.create("t1", new Message("user", "hello"));
        task.transitionTo(TaskState.WORKING);

        assertTrue(store.cancel("t1"));
        assertEquals(TaskState.CANCELED, store.get("t1").get().getStatus());
    }

    @Test
    void cancel_terminal_task_returns_false() {
        A2ATask task = store.create("t1", new Message("user", "hello"));
        task.transitionTo(TaskState.COMPLETED);

        assertFalse(store.cancel("t1"));
    }

    @Test
    void cancel_nonexistent_returns_false() {
        assertFalse(store.cancel("nonexistent"));
    }

    @Test
    void size() {
        assertEquals(0, store.size());
        store.create("t1", new Message("user", "hello"));
        store.create("t2", new Message("user", "world"));
        assertEquals(2, store.size());
    }
}
