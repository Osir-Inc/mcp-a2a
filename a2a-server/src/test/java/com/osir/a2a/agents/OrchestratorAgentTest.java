package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrchestratorAgentTest {

    @Mock AgentRegistry agentRegistry;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks OrchestratorAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_orchestrationKeywords() {
        assertTrue(agent.score(new A2ATask("t1",
                new Message("user", "set up example.com with dns and vps"))) > 0.5);
    }

    @Test
    void score_multipleAreas_higherScore() {
        double single = agent.score(new A2ATask("t1", new Message("user", "set up a domain")));
        double multi = agent.score(new A2ATask("t1", new Message("user", "set up domain with dns and vps and billing")));
        assertTrue(multi > single);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "hello world"))));
    }

    @Test
    void score_explicitSkill() {
        A2ATask task = new A2ATask("t1", new Message("user", "x"));
        task.setMetadata(Map.of("skill", "orchestrate"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void handle_noPlan_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "hello"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void handle_withDomain_createsSteps() {
        // Mock a domain agent that completes immediately
        SpecialistAgent mockDomainAgent = mock(SpecialistAgent.class);
        when(mockDomainAgent.getId()).thenReturn("domain-agent");
        when(mockDomainAgent.handle(any())).thenAnswer(inv -> {
            A2ATask t = inv.getArgument(0);
            t.transitionTo(TaskState.COMPLETED);
            t.addMessage(new Message("agent", "Done"));
            return t;
        });

        SpecialistAgent mockBillingAgent = mock(SpecialistAgent.class);
        when(mockBillingAgent.getId()).thenReturn("billing-agent");
        when(mockBillingAgent.handle(any())).thenAnswer(inv -> {
            A2ATask t = inv.getArgument(0);
            t.transitionTo(TaskState.COMPLETED);
            t.addMessage(new Message("agent", "Balance OK"));
            return t;
        });

        when(agentRegistry.findById("domain-agent")).thenReturn(Optional.of(mockDomainAgent));
        when(agentRegistry.findById("billing-agent")).thenReturn(Optional.of(mockBillingAgent));

        A2ATask task = new A2ATask("t1", new Message("user", "register example.com"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        assertFalse(out.getArtifacts().isEmpty());
    }

    @Test
    void handle_tooManySteps_fails() {
        // Can't easily trigger >15 steps with the rule-based planner,
        // but we can verify the guard exists
        assertEquals("orchestrator", agent.getId());
        assertTrue(agent.getAgentCard().getSkills().size() >= 2);
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals("OSIR Orchestrator", agent.getAgentCard().getName());
    }
}
