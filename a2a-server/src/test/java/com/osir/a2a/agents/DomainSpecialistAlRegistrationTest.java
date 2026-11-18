package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.Message;
import com.osir.a2a.protocol.TaskState;
import com.osir.mcp.models.DomainRegistrationResult;
import com.osir.mcp.models.contact.RegistryAttributes;
import com.osir.mcp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A2A register_domain: nameservers + registrantId + AKEP fields reach the backend; bad .al orders never stage. */
class DomainSpecialistAlRegistrationTest {

    @Mock DomainService domainService;
    @Mock DomainSuggestionService suggestionService;
    @Mock TransferService transferService;
    @Mock HostService hostService;
    @Mock CatalogService catalogService;
    @Mock ContactService contactService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks DomainSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        GateTestSupport.wire(agent);
        agent.init();
    }

    private A2ATask task(String text, Map<String, Object> extra) {
        A2ATask task = new A2ATask("t1", new Message("user", text));
        Map<String, Object> md = new HashMap<>(extra);
        md.put("skill", "register_domain");
        task.setMetadata(md);
        return task;
    }

    @Test
    void noNameservers_asksForThem() {
        A2ATask result = agent.handle(task("register avdia.al", Map.of("registrantId", "7360")));
        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertNull(GateTestSupport.stagedActionId(result));
    }

    @Test
    void anyTld_withoutRegistrantId_asksInsteadOfDefaultContact() {
        A2ATask result = agent.handle(task("register foo.com", Map.of("nameservers", "ns1.osir.com,ns3.osir.com")));
        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertNull(GateTestSupport.stagedActionId(result));
    }

    @Test
    void nonAl_jsonStringNameservers_parsed_attributesNotForwarded() {
        when(domainService.registerDomain(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(new DomainRegistrationResult("foo.com", true, "ok"));
        A2ATask staged = agent.handle(task("register foo.com", Map.of("registrantId", "5",
                "nameservers", "[\"ns1.osir.com\",\"ns3.osir.com\"]", "entityType", "Person Natyror")));
        agent.handle(GateTestSupport.confirmOn(staged));
        verify(domainService).registerDomain(eq("foo.com"), eq(1), isNull(), eq(List.of("ns1.osir.com", "ns3.osir.com")),
                eq(true), eq(true), isNull(), eq(5L), isNull());
        verifyNoInteractions(contactService);
    }

    @Test
    void al_incompleteContact_notStaged() {
        when(contactService.alRegistrantProblem(7360L, null)).thenReturn("Contact 7360 is missing .al registry fields: nipt");
        A2ATask result = agent.handle(task("register oletros.al",
                Map.of("registrantId", "7360", "nameservers", List.of("ns1.osir.com", "ns3.osir.com"))));
        assertNull(GateTestSupport.stagedActionId(result));
        assertTrue(result.getHistory().stream().anyMatch(m -> m.getTextContent().contains("nipt")));
    }

    @Test
    void al_stageThenConfirm_forwardsRegistrantNameserversAndAttributes() {
        when(contactService.alRegistrantProblem(eq(7360L), any())).thenReturn(null);
        when(domainService.registerDomain(any(), anyInt(), any(), any(), anyBoolean(), anyBoolean(), any(), any(), any()))
                .thenReturn(new DomainRegistrationResult("avdia.al", true, "pendingCreate"));

        A2ATask staged = agent.handle(task("register avdia.al", Map.of("registrantId", "7360",
                "nameservers", "ns1.osir.com, ns3.osir.com", "entityType", "Person Natyror", "doctype", "op", "docnr", "J75831039W")));
        assertNotNull(GateTestSupport.stagedActionId(staged));
        verifyNoInteractions(domainService);

        agent.handle(GateTestSupport.confirmOn(staged));

        ArgumentCaptor<RegistryAttributes> attrs = ArgumentCaptor.forClass(RegistryAttributes.class);
        verify(domainService).registerDomain(eq("avdia.al"), eq(1), isNull(), eq(List.of("ns1.osir.com", "ns3.osir.com")),
                eq(true), eq(true), isNull(), eq(7360L), attrs.capture());
        assertEquals("J75831039W", attrs.getValue().getDocnr());
        assertEquals("Person Natyror", attrs.getValue().getEntityType());
    }
}
