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

    /**
     * Every branch matches on `skill || text`, so free text must not be able to steer a request that
     * already carries an explicit skill. "ssh" in the sentence used to hijack this into listing keys.
     */
    @Test
    void handle_explicitSkillWinsOverTextKeywords() {
        when(vpsService.deleteInstance("vps-1"))
                .thenReturn(new com.osir.mcp.models.vps.VpsActionResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "terminate the server, I lost the ssh key"));
        task.setMetadata(Map.of("skill", "delete_vps", "instanceId", "vps-1"));
        agent.handle(task);

        verify(vpsService).deleteInstance("vps-1");
        verify(vpsService, never()).listSshKeys();
    }

    /**
     * "which templates can I reinstall this server with?" is a read-only question that happens to
     * contain "reinstall". Landing it on the build branch answers it with an ERASES-ALL-DATA prompt,
     * so the listing has to win when the text mentions templates at all.
     */
    @Test
    void handle_templateQuestionMentioningReinstallListsRatherThanBuilds() {
        when(vpsService.listOsTemplates(null, "vps-1", null))
                .thenReturn(new com.osir.mcp.models.vps.VpsOsTemplateListResult(true, "OK"));

        A2ATask task = new A2ATask("t1",
                new Message("user", "which templates can I reinstall this server with?"));
        task.setMetadata(Map.of("instanceId", "vps-1"));
        agent.handle(task);

        verify(vpsService).listOsTemplates(null, "vps-1", null);
        verify(vpsService, never()).buildInstance(any(), any(), any(), any(), any());
    }

    /** The pre-order lookup routes on packageId, with no instance in play. */
    @Test
    void handle_listOsTemplatesByPackage() {
        when(vpsService.listOsTemplates("pkg-1", null, null))
                .thenReturn(new com.osir.mcp.models.vps.VpsOsTemplateListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "list OS templates"));
        task.setMetadata(Map.of("packageId", "pkg-1"));
        agent.handle(task);

        verify(vpsService).listOsTemplates("pkg-1", null, null);
    }

    /** An explicit skill still builds, even though "template" appears in the text. */
    @Test
    void handle_explicitBuildSkillStillBuildsDespiteTemplateWord() {
        when(vpsService.buildInstance("vps-1", 46, null, null, null))
                .thenReturn(new com.osir.mcp.models.vps.VpsBuildResult(true, "queued"));

        A2ATask task = new A2ATask("t1", new Message("user", "install template 46"));
        task.setMetadata(Map.of("skill", "build_vps", "instanceId", "vps-1",
                "operatingSystemId", "46", "confirm", "ERASE"));
        agent.handle(task);

        verify(vpsService).buildInstance("vps-1", 46, null, null, null);
    }

    /** "os" used to match cost/host/most/close as a bare substring and steal the whole conversation. */
    @Test
    void score_doesNotMatchOsInsideUnrelatedWords() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "what is the cost?"))));
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
