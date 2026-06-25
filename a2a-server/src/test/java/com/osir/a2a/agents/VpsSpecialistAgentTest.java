package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.vps.VpsPackageListResult;
import com.osir.mcp.models.vps.VpsLocationListResult;
import com.osir.mcp.models.vps.VpsInstanceListResult;
import com.osir.mcp.services.VpsService;
import com.osir.mcp.services.CatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VpsSpecialistAgentTest {

    @Mock VpsService vpsService;
    @Mock CatalogService catalogService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks VpsSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_vpsKeywords() {
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "list vps packages"))) > 0.4);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "register domain"))));
    }

    @Test
    void score_explicitAgent() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("agent", "vps-agent"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void handle_listPackages() {
        when(vpsService.listPackages()).thenReturn(new VpsPackageListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "show vps packages"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(vpsService).listPackages();
    }

    @Test
    void handle_listLocations() {
        when(vpsService.listLocations()).thenReturn(new VpsLocationListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "vps datacenter locations"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(vpsService).listLocations();
    }

    @Test
    void handle_listInstances() {
        when(vpsService.listMyInstances()).thenReturn(new VpsInstanceListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "list my vps instances"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_orderVps_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "order a new vps server"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertTrue(agent.getAgentCard().getSkills().size() >= 7);
    }
}
