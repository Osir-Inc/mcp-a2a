package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.A2ATask;
import com.osir.a2a.protocol.Message;
import com.osir.a2a.protocol.TaskState;
import com.osir.mcp.models.transfer.TransferInitiateResult;
import com.osir.mcp.services.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A2A transfer_domain: the owner (registrantId) reaches the backend instead of the account default contact. */
class DomainSpecialistTransferTest {

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
        md.put("skill", "transfer_domain");
        task.setMetadata(md);
        return task;
    }

    @Test
    void withoutRegistrantId_asksInsteadOfDefaultContact() {
        A2ATask result = agent.handle(task("transfer best000.com auth code QX7-2291", Map.of()));
        assertEquals(TaskState.INPUT_REQUIRED, result.getStatus());
        assertNull(GateTestSupport.stagedActionId(result));
    }

    @Test
    void stageThenConfirm_forwardsAuthCodeMetadataAndRegistrant() {
        when(transferService.initiateTransfer(any(), any(), any(), any()))
                .thenReturn(new TransferInitiateResult(true, "ok"));

        A2ATask staged = agent.handle(task("transfer best000.com", Map.of("authCode", "QX7-2291", "registrantId", "7360")));
        assertNotNull(GateTestSupport.stagedActionId(staged));
        verifyNoInteractions(transferService);

        agent.handle(GateTestSupport.confirmOn(staged));
        verify(transferService).initiateTransfer(eq("best000.com"), eq("QX7-2291"), eq(7360L), isNull());
    }
}
