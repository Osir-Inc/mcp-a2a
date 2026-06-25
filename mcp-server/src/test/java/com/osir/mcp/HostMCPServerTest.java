package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.host.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.HostService;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HostMCPServerTest {

    @Mock
    HostService hostService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    HostMCPServer mcpServer;

    private static final String TEST_HOSTNAME = "ns1.example.com";
    private static final String TEST_DOMAIN = "example.com";
    private static final List<String> TEST_IPS = Arrays.asList("192.0.2.1", "198.51.100.1");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    // ===== checkHostAvailability =====

    @Test
    void checkHostAvailability_delegatesToService() {
        HostCheckResult expected = new HostCheckResult(true, "Available", true, TEST_HOSTNAME);
        when(hostService.checkAvailability(TEST_HOSTNAME)).thenReturn(expected);

        HostCheckResult result = mcpServer.checkHostAvailability(TEST_HOSTNAME, mockConnection);

        assertSame(expected, result);
        verify(hostService).checkAvailability(TEST_HOSTNAME);
    }

    @Test
    void checkHostAvailability_handlesException() {
        when(hostService.checkAvailability(TEST_HOSTNAME)).thenThrow(new RuntimeException("Unexpected"));

        HostCheckResult result = mcpServer.checkHostAvailability(TEST_HOSTNAME, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Unexpected"));
        assertFalse(result.isAvailable());
        assertEquals(TEST_HOSTNAME, result.getHostname());
    }

    // ===== createHost =====

    @Test
    void createHost_delegatesToService() {
        HostRecord record = new HostRecord(TEST_HOSTNAME, TEST_IPS, TEST_DOMAIN, "2026-02-19T10:00:00Z");
        HostResult expected = new HostResult(true, "Created", record);
        when(hostService.createHost(TEST_HOSTNAME, TEST_IPS)).thenReturn(expected);

        HostResult result = mcpServer.createHost(TEST_HOSTNAME, TEST_IPS, mockConnection);

        assertSame(expected, result);
        verify(hostService).createHost(TEST_HOSTNAME, TEST_IPS);
    }

    @Test
    void createHost_handlesException() {
        when(hostService.createHost(TEST_HOSTNAME, TEST_IPS)).thenThrow(new RuntimeException("Fail"));

        HostResult result = mcpServer.createHost(TEST_HOSTNAME, TEST_IPS, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
        assertNull(result.getRecord());
    }

    // ===== getHostsForDomain =====

    @Test
    void getHostsForDomain_delegatesToService() {
        HostListResult expected = new HostListResult(true, "Retrieved", Arrays.asList(
                new HostRecord("ns1.example.com", Arrays.asList("192.0.2.1"), TEST_DOMAIN, "2026-01-01T00:00:00Z")
        ));
        when(hostService.getHostsForDomain(TEST_DOMAIN)).thenReturn(expected);

        HostListResult result = mcpServer.getHostsForDomain(TEST_DOMAIN, mockConnection);

        assertSame(expected, result);
        verify(hostService).getHostsForDomain(TEST_DOMAIN);
    }

    @Test
    void getHostsForDomain_handlesException() {
        when(hostService.getHostsForDomain(TEST_DOMAIN)).thenThrow(new RuntimeException("Fail"));

        HostListResult result = mcpServer.getHostsForDomain(TEST_DOMAIN, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
        assertNull(result.getHosts());
    }

    // ===== deleteHost =====

    @Test
    void deleteHost_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "deleteHost", "summary");
        when(pendingActionStore.stage(eq("deleteHost"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.deleteHost(TEST_HOSTNAME, mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("deleteHost"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }
}
