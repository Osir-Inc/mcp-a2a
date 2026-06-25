package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.AuthStatusResult;
import com.osir.mcp.models.account.AccountSummaryResult;
import com.osir.mcp.models.account.UserProfileResult;
import com.osir.mcp.models.audit.RecentActivityResult;
import com.osir.mcp.services.AccountService;
import com.osir.mcp.services.AuditService;
import com.osir.mcp.services.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountSpecialistAgentTest {

    @Mock AccountService accountService;
    @Mock AuditService auditService;
    @Mock AuthService authService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks AccountSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_accountKeywords() {
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "show my account summary"))) > 0.4);
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "who am i"))) > 0.2);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "register domain"))));
    }

    @Test
    void score_explicitSkill() {
        A2ATask task = new A2ATask("t1", new Message("user", "x"));
        task.setMetadata(Map.of("skill", "get_profile"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void handle_profile() {
        when(accountService.getMyProfile()).thenReturn(new UserProfileResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "show my profile"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(accountService).getMyProfile();
    }

    @Test
    void handle_summary() {
        when(accountService.getAccountSummary()).thenReturn(new AccountSummaryResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "account summary overview"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_authStatus() {
        when(authService.getAuthStatus()).thenReturn(new AuthStatusResult(true, "john", 3600L));

        A2ATask task = new A2ATask("t1", new Message("user", "auth status"));
        task.setMetadata(Map.of("skill", "get_auth_status"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_recentActivity() {
        when(auditService.getRecentActivity()).thenReturn(new RecentActivityResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "show recent activity"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_domainAudit_noDomain_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "show domain audit trail"));
        task.setMetadata(Map.of("skill", "get_domain_audit"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals("OSIR Account & Audit Agent", agent.getAgentCard().getName());
    }
}
