package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.contact.RegistrantInfo;
import com.osir.mcp.models.contact.RegistryAttributes;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.ContactService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** registerDomain refuses before staging when the owner is missing or a .al registrant is incomplete. */
class DomainRegistrarAlRegistrationTest {

    @Mock ContactService contactService;
    @Mock PendingActionStore pendingActionStore;
    @Mock McpConnection connection;
    @InjectMocks DomainRegistrarMCPServer server;

    private static final List<String> NS = List.of("ns1.osir.com", "ns3.osir.com");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(connection.id()).thenReturn("c1");
        when(pendingActionStore.stage(any(), any(), any(), any(), any())).thenReturn(new ConfirmationRequiredResult());
    }

    private ConfirmationRequiredResult register(String domain, RegistrantInfo inline, Long registrantId, RegistryAttributes attrs) {
        return server.registerDomain(domain, 1, inline, NS, null, null, null, registrantId, attrs, null, connection);
    }

    @Test
    void al_problem_refusedBeforeStaging() {
        when(contactService.alRegistrantProblem(7360L, null)).thenReturn("Contact 7360 is missing .al registry fields: nipt");
        ToolCallException e = assertThrows(ToolCallException.class, () -> register("Oletros.AL", null, 7360L, null));
        assertTrue(e.getMessage().contains("nipt"));
        verifyNoInteractions(pendingActionStore);
    }

    @Test
    void al_ok_stages() {
        when(contactService.alRegistrantProblem(7360L, null)).thenReturn(null);
        assertNotNull(register("avdia.al", null, 7360L, null));
        verify(pendingActionStore).stage(any(), any(), any(), any(), any());
    }

    @Test
    void nonAl_skipsAlCheck() {
        assertNotNull(register("example.com", new RegistrantInfo(), null, null));
        verify(contactService, never()).alRegistrantProblem(any(), any());
    }

    @Test
    void noOwnerAtAll_refused() {
        assertThrows(ToolCallException.class, () -> register("example.com", null, null, null));
        verifyNoInteractions(pendingActionStore);
    }
}
