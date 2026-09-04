package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.*;
import com.osir.mcp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DomainSpecialistAgentTest {

    @Mock DomainService domainService;
    @Mock DomainSuggestionService suggestionService;
    @Mock TransferService transferService;
    @Mock HostService hostService;
    @Mock com.osir.mcp.services.CatalogService catalogService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    DomainSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        GateTestSupport.wire(agent);
        // Trigger @PostConstruct manually since Mockito doesn't call it
        agent.init();
    }

    // --- Scoring ---

    @Test
    void score_domainKeywords_highScore() {
        A2ATask task = new A2ATask("t1", new Message("user", "register my domain example.com"));
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
        task.setMetadata(Map.of("skill", "check_availability"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void score_explicitAgent_match() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("agent", "domain-agent"));
        assertEquals(1.0, agent.score(task));
    }

    @Test
    void score_explicitAgent_noMatch() {
        A2ATask task = new A2ATask("t1", new Message("user", "do it"));
        task.setMetadata(Map.of("agent", "billing-agent"));
        assertEquals(0.0, agent.score(task));
    }

    // --- Skill-based routing ---

    @Test
    void handle_explicitSkill_checkAvailability() {
        A2ATask task = new A2ATask("t1", new Message("user", "example.com"));
        task.setMetadata(Map.of("skill", "check_availability"));

        when(domainService.checkAvailability("example.com"))
                .thenReturn(new DomainAvailabilityResult("example.com", true, "Domain is available"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        assertFalse(result.getArtifacts().isEmpty());
        verify(domainService).checkAvailability("example.com");
    }

    @Test
    void handle_explicitSkill_listDomains() {
        A2ATask task = new A2ATask("t1", new Message("user", "show me"));
        task.setMetadata(Map.of("skill", "list_domains"));

        when(domainService.getUserDomains())
                .thenReturn(new UserDomainsResult(true, "3 domains found"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        verify(domainService).getUserDomains();
    }

    // --- Intent-based routing ---

    @Test
    void handle_checkAvailability_byIntent() {
        A2ATask task = new A2ATask("t1", new Message("user", "check if example.com is available"));

        when(domainService.checkAvailability("example.com"))
                .thenReturn(new DomainAvailabilityResult("example.com", false, "Already registered"));

        A2ATask result = agent.handle(task);
        assertEquals(TaskState.COMPLETED, result.getStatus());
    }

    @Test
    void handle_autoRenew_takePriorityOverRenew() {
        // "auto-renew" should NOT fall through to "renew"
        A2ATask task = new A2ATask("t1", new Message("user", "enable auto-renew for example.com"));

        when(domainService.updateAutoRenew("example.com", true))
                .thenReturn(new DomainActionResult(true, "Auto-renew enabled", "example.com", "enabled"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.COMPLETED, result.getStatus());
        verify(domainService).updateAutoRenew("example.com", true);
        verify(domainService, never()).renewDomain(anyString(), anyInt());
    }

    @Test
    void handle_unlock_takePriorityOverLock() {
        A2ATask task = new A2ATask("t1", new Message("user", "unlock example.com"));

        when(domainService.unlockDomain("example.com"))
                .thenReturn(new DomainActionResult(true, "Unlocked", "example.com", "unlocked"));

        A2ATask result = agent.handle(task);

        verify(domainService).unlockDomain("example.com");
        verify(domainService, never()).lockDomain(anyString());
    }

    // --- Domain extraction ---

    @Test
    void extractDomain_standardTld() {
        assertEquals("example.com", agent.extractDomain("check example.com please"));
        assertEquals("test.io", agent.extractDomain("is test.io available?"));
        assertEquals("my-site.xyz", agent.extractDomain("register my-site.xyz"));
    }

    @Test
    void extractDomain_skipFileExtensions() {
        assertNull(agent.extractDomain("edit file.java"));
        assertNull(agent.extractDomain("fix test.go"));
        assertNull(agent.extractDomain("update config.json"));
    }

    @Test
    void extractDomain_noDomain() {
        assertNull(agent.extractDomain("list all my domains"));
    }

    // --- INPUT_REQUIRED states ---

    @Test
    void handle_noDomain_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "register a domain"));
        task.setMetadata(Map.of("skill", "register_domain"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertTrue(result.getHistory().stream()
                .anyMatch(m -> "agent".equals(m.getRole()) && m.getTextContent().contains("domain name")));
    }

    @Test
    void handle_transfer_noAuthCode_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "transfer example.com"));

        A2ATask result = agent.handle(task);

        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertTrue(result.getHistory().stream()
                .anyMatch(m -> m.getTextContent().contains("EPP code")));
    }

    // ---- Skills that existed as MCP tools long before they reached A2A (drift closed 2026-09-04) ----

    @Test
    void handle_transferQuote_routesToTransferService() {
        var quote = new com.osir.mcp.models.transfer.TransferQuoteResult(true, "OK");
        when(transferService.getQuote("example.com")).thenReturn(quote);

        A2ATask task = new A2ATask("t1", new Message("user", "what would it cost to move example.com here"));
        task.setMetadata(Map.of("skill", "get_transfer_quote"));

        A2ATask out = agent.handle(task);
        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(transferService).getQuote("example.com");
        // The word "move" must not reach the transfer-initiate path, which spends money.
        verify(transferService, never()).initiateTransfer(anyString(), anyString());
    }

    @Test
    void handle_hostsForDomain_routesToHostService() {
        var hosts = new com.osir.mcp.models.host.HostListResult(true, "OK");
        when(hostService.getHostsForDomain("example.com")).thenReturn(hosts);

        A2ATask task = new A2ATask("t1", new Message("user", "glue records on example.com"));
        task.setMetadata(Map.of("skill", "get_hosts_for_domain"));

        assertEquals(TaskState.COMPLETED, agent.handle(task).getStatus());
        verify(hostService).getHostsForDomain("example.com");
    }

    @Test
    void handle_updateNameservers_needsTheListInMetadata() {
        A2ATask task = new A2ATask("t1", new Message("user", "repoint example.com"));
        task.setMetadata(Map.of("skill", "update_nameservers"));

        assertEquals(TaskState.INPUT_REQUIRED, agent.handle(task).getStatus());
        verify(domainService, never()).updateNameservers(anyString(), anyList());
    }

    @Test
    void handle_updateNameservers_splitsTheCsv() {
        var result = new com.osir.mcp.models.NameserverUpdateResult("example.com", true, "updated");
        when(domainService.updateNameservers(eq("example.com"), anyList())).thenReturn(result);

        A2ATask task = new A2ATask("t1", new Message("user", "repoint it"));
        task.setMetadata(Map.of("skill", "update_nameservers", "domain", "example.com",
                "nameservers", "ns1.osir.com, ns3.osir.com"));

        assertEquals(TaskState.COMPLETED, agent.handle(task).getStatus());
        verify(domainService).updateNameservers("example.com", List.of("ns1.osir.com", "ns3.osir.com"));
    }

    // --- Agent card ---

    @Test
    void getAgentCard_isCached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
    }

    @Test
    void getAgentCard_hasSkills() {
        AgentCard card = agent.getAgentCard();
        assertEquals("OSIR Domain Agent", card.getName());
        assertTrue(card.getSkills().size() >= 10);
    }
}
