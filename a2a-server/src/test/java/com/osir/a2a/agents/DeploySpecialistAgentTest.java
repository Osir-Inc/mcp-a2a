package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppLogsResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.services.DeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeploySpecialistAgentTest {

    @Mock DeploymentService deploymentService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    DeploySpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Trigger @PostConstruct manually since Mockito doesn't call it
        agent.init();
    }

    // --- Scoring ---

    @Test
    void score_deployKeywords_highScore() {
        A2ATask task = new A2ATask("t1", new Message("user", "deploy my website please"));
        assertTrue(agent.score(task) > 0.5);
    }

    @Test
    void score_noKeywords_zeroScore() {
        A2ATask task = new A2ATask("t1", new Message("user", "what is the weather today?"));
        assertEquals(0.0, agent.score(task));
    }

    @Test
    void score_explicitSkill_maxScore() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("skill", "list_apps"));
        assertEquals(1.0, agent.score(task));
    }

    // --- Skill-based routing ---

    @Test
    void handle_explicitSkill_listApps() {
        A2ATask task = new A2ATask("t1", new Message("user", "show me"));
        task.setMetadata(Map.of("skill", "list_apps"));

        when(deploymentService.listApps())
                .thenReturn(new AppListResult(true, "2 app(s).", List.of()));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        assertFalse(result.getArtifacts().isEmpty());
        verify(deploymentService).listApps();
    }

    @Test
    void handle_explicitSkill_getAppStatus() {
        A2ATask task = new A2ATask("t1", new Message("user", "how is it doing?"));
        task.setMetadata(Map.of("skill", "get_app_status", "appId", "app-123"));

        when(deploymentService.getStatus("app-123"))
                .thenReturn(new AppStatusResult(true, "OK", null, null, "READY", List.of(), null, null, null, null));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        verify(deploymentService).getStatus("app-123");
    }

    @Test
    void handle_getAppLogs_withTail() {
        A2ATask task = new A2ATask("t1", new Message("user", "logs please"));
        task.setMetadata(Map.of("skill", "get_app_logs", "appId", "app-123", "tail", "50"));

        when(deploymentService.getLogs("app-123", 50))
                .thenReturn(new AppLogsResult(true, "OK", "line1\nline2"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        verify(deploymentService).getLogs("app-123", 50);
    }

    @Test
    void handle_deployApp_withMetadata() {
        A2ATask task = new A2ATask("t1", new Message("user", "deploy it"));
        task.setMetadata(Map.of("skill", "deploy_app",
                "name", "my-shop", "language", "node", "uploadTicket", "ticket-1"));

        when(deploymentService.deploy("my-shop", "node", null, "ticket-1"))
                .thenReturn(new DeployResult(true, "Deploy started for 'my-shop'.",
                        "app-123", "https://my-shop.osir.app", "BUILDING"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        verify(deploymentService).deploy("my-shop", "node", null, "ticket-1");
    }

    // --- INPUT_REQUIRED states ---

    @Test
    void handle_getAppStatus_noAppId_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "check it"));
        task.setMetadata(Map.of("skill", "get_app_status"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertTrue(result.getHistory().stream()
                .anyMatch(m -> "agent".equals(m.getRole()) && m.getTextContent().contains("appId")));
        verifyNoInteractions(deploymentService);
    }

    @Test
    void handle_deployApp_missingParams_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "deploy my app"));
        task.setMetadata(Map.of("skill", "deploy_app", "name", "my-shop"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertTrue(result.getHistory().stream()
                .anyMatch(m -> "agent".equals(m.getRole()) && m.getTextContent().contains("language")));
        verifyNoInteractions(deploymentService);
    }

    // --- Failure handling ---

    @Test
    void handle_serviceThrows_failsTask() {
        A2ATask task = new A2ATask("t1", new Message("user", "show apps"));
        task.setMetadata(Map.of("skill", "list_apps"));

        when(deploymentService.listApps()).thenThrow(new RuntimeException("Connection refused"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.FAILED, result.getStatus());
        assertTrue(result.getHistory().stream()
                .anyMatch(m -> "agent".equals(m.getRole()) && m.getTextContent().contains("unavailable")));
    }

    @Test
    void handle_serviceReportsFailure_failsTask() {
        A2ATask task = new A2ATask("t1", new Message("user", "show apps"));
        task.setMetadata(Map.of("skill", "list_apps"));

        when(deploymentService.listApps())
                .thenReturn(AppListResult.fail("Could not list apps right now. Please try again."));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.FAILED, result.getStatus());
    }

    // --- Agent card ---

    @Test
    void getAgentCard_isCachedAndHasSkills() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        AgentCard card = agent.getAgentCard();
        assertEquals("OSIR Deploy Agent", card.getName());
        assertEquals(4, card.getSkills().size());
        assertTrue(card.getSkills().stream().noneMatch(s -> s.getId().contains("delete")));
    }
}
