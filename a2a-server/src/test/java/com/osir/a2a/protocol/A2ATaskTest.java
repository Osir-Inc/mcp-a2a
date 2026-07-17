package com.osir.a2a.protocol;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class A2ATaskTest {

    @Test
    void lifecycle_submitted_to_completed() {
        A2ATask task = new A2ATask("t1", new Message("user", "check example.com"));
        assertEquals(TaskState.SUBMITTED, task.getStatus());
        assertFalse(task.isTerminal());

        task.transitionTo(TaskState.WORKING);
        assertEquals(TaskState.WORKING, task.getStatus());
        assertFalse(task.isTerminal());

        task.transitionTo(TaskState.COMPLETED);
        assertTrue(task.isTerminal());
    }

    @Test
    void addMessage_and_artifact() {
        A2ATask task = new A2ATask("t1", new Message("user", "hello"));
        assertEquals(1, task.getHistory().size());

        task.addMessage(new Message("agent", "hi there"));
        assertEquals(2, task.getHistory().size());
        assertEquals("agent", task.getHistory().get(1).getRole());

        task.addArtifact(Artifact.ofData("result", Map.of("key", "value")));
        assertEquals(1, task.getArtifacts().size());
        assertEquals("result", task.getArtifacts().get(0).getName());
    }

    @Test
    void isTerminal_covers_all_terminal_states() {
        A2ATask task = new A2ATask("t1", new Message("user", "test"));

        task.transitionTo(TaskState.FAILED);
        assertTrue(task.isTerminal());

        task.transitionTo(TaskState.CANCELED);
        assertTrue(task.isTerminal());

        task.transitionTo(TaskState.INPUT_REQUIRED);
        assertFalse(task.isTerminal());
    }
}
