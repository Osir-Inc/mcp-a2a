package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.contact.ContactListResult;
import com.osir.mcp.services.ContactService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContactSpecialistAgentTest {

    @Mock ContactService contactService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks ContactSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_contactKeywords() {
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "list my contacts"))) > 0.2);
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "update registrant contact info"))) > 0.4);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "check dns records"))));
    }

    @Test
    void handle_listContacts() {
        when(contactService.listContacts(null)).thenReturn(new ContactListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "list all contacts"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(contactService).listContacts(null);
    }

    @Test
    void handle_createContact_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "create a new contact"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void handle_deleteContact_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "delete contact"));
        task.setMetadata(Map.of("skill", "delete_contact"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void handle_getContact_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "get contact"));
        task.setMetadata(Map.of("skill", "get_contact"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals(6, agent.getAgentCard().getSkills().size());
    }
}
