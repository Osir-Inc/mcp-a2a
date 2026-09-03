package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.mail.MailDomainEnableResult;
import com.osir.mcp.models.mail.MailPlanListResult;
import com.osir.mcp.services.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MailSpecialistAgentTest {

    @Mock MailService mailService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks MailSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_mailKeywords() {
        A2ATask task = new A2ATask("t1", new Message("user", "set up email and a mailbox for example.com"));
        assertTrue(agent.score(task) > 0.5);
    }

    @Test
    void score_explicitSkill() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("skill", "list_mail_plans"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void handle_listPlans_success() {
        MailPlanListResult result = new MailPlanListResult(true, "OK");
        when(mailService.listPlans()).thenReturn(result);

        A2ATask task = new A2ATask("t1", new Message("user", "show me the plans"));
        task.setMetadata(Map.of("skill", "list_mail_plans"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(mailService).listPlans();
    }

    @Test
    void handle_enableDomain_metadataDomain() {
        MailDomainEnableResult result = new MailDomainEnableResult(true, "Enabled");
        when(mailService.enableDomain("example.com", null, null, null)).thenReturn(result);

        A2ATask task = new A2ATask("t1", new Message("user", "enable email"));
        task.setMetadata(Map.of("skill", "enable_mail_domain", "domain", "example.com"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(mailService).enableDomain("example.com", null, null, null);
    }

    @Test
    void handle_enableDomain_noDomain_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "enable email hosting"));
        task.setMetadata(Map.of("skill", "enable_mail_domain"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
        verifyNoInteractions(mailService);
    }

    @Test
    void handle_quote_missingPackageId_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "how much is a mailbox?"));
        task.setMetadata(Map.of("skill", "get_mailbox_quote"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
        verifyNoInteractions(mailService);
    }

    @Test
    void handle_serviceException_failsTask() {
        when(mailService.listPlans()).thenThrow(new RuntimeException("backend down"));

        A2ATask task = new A2ATask("t1", new Message("user", "list plans"));
        task.setMetadata(Map.of("skill", "list_mail_plans"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.FAILED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals("OSIR Email Agent", agent.getAgentCard().getName());
    }
}
