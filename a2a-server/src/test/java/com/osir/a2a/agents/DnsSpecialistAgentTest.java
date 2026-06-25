package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.dns.DnsRecordListResult;
import com.osir.mcp.models.dns.DnsActionResult;
import com.osir.mcp.services.DnsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DnsSpecialistAgentTest {

    @Mock DnsService dnsService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks DnsSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_dnsKeywords() {
        A2ATask task = new A2ATask("t1", new Message("user", "list dns records for example.com"));
        assertTrue(agent.score(task) > 0.5);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "what is the weather?"))));
    }

    @Test
    void score_explicitSkill() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("skill", "list_dns_records"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void handle_listRecords_success() {
        DnsRecordListResult result = new DnsRecordListResult(true, "OK");
        when(dnsService.listRecords("example.com")).thenReturn(result);

        A2ATask task = new A2ATask("t1", new Message("user", "list dns records for example.com"));
        task.setMetadata(Map.of("skill", "list_dns_records"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(dnsService).listRecords("example.com");
    }

    @Test
    void handle_noDomain_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "list dns records"));
        task.setMetadata(Map.of("skill", "list_dns_records"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void handle_createRecord_asksForDetails() {
        A2ATask task = new A2ATask("t1", new Message("user", "create a record for example.com"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals("OSIR DNS Agent", agent.getAgentCard().getName());
    }
}
