package com.osir.mcp.services;

import com.osir.mcp.clients.HostBackendClient;
import com.osir.mcp.models.host.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HostServiceTest {

    @Mock
    HostBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    HostService hostService;

    private static final String TEST_HOSTNAME = "ns1.example.com";
    private static final String TEST_DOMAIN = "example.com";
    private static final String BEARER_TOKEN = "Bearer test-token";
    private static final List<String> TEST_IPS = Arrays.asList("192.0.2.1", "198.51.100.1");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void mockAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(BEARER_TOKEN);
    }

    private void mockUnauthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
    }

    // ===== checkAvailability tests =====

    @Test
    void checkAvailability_success() {
        mockAuthenticated();
        HostCheckResponse response = new HostCheckResponse(true, TEST_HOSTNAME, "Hostname is available");
        when(backendClient.checkHostAvailability(any(HostCreateRequest.class), eq(BEARER_TOKEN)))
                .thenReturn(response);

        HostCheckResult result = hostService.checkAvailability(TEST_HOSTNAME);

        assertTrue(result.isSuccess());
        assertTrue(result.isAvailable());
        assertEquals(TEST_HOSTNAME, result.getHostname());
        assertEquals("Hostname is available", result.getMessage());
    }

    @Test
    void checkAvailability_unauthenticated() {
        mockUnauthenticated();

        HostCheckResult result = hostService.checkAvailability(TEST_HOSTNAME);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required. Please use loginWithDevice to authenticate.", result.getMessage());
        assertFalse(result.isAvailable());
        assertEquals(TEST_HOSTNAME, result.getHostname());
        verifyNoInteractions(backendClient);
    }

    @Test
    void checkAvailability_backendException() {
        mockAuthenticated();
        when(backendClient.checkHostAvailability(any(HostCreateRequest.class), eq(BEARER_TOKEN)))
                .thenThrow(new RuntimeException("Connection refused"));

        HostCheckResult result = hostService.checkAvailability(TEST_HOSTNAME);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
        assertFalse(result.isAvailable());
        assertEquals(TEST_HOSTNAME, result.getHostname());
    }

    // ===== createHost tests =====

    @Test
    void createHost_success() {
        mockAuthenticated();
        HostRecord record = new HostRecord(TEST_HOSTNAME, TEST_IPS, TEST_DOMAIN, "2026-02-19T10:00:00Z");
        when(backendClient.createHost(any(HostCreateRequest.class), eq(BEARER_TOKEN)))
                .thenReturn(record);

        HostResult result = hostService.createHost(TEST_HOSTNAME, TEST_IPS);

        assertTrue(result.isSuccess());
        assertEquals("Host record created successfully", result.getMessage());
        assertNotNull(result.getRecord());
        assertEquals(TEST_HOSTNAME, result.getRecord().getHostname());
        assertEquals(TEST_IPS, result.getRecord().getIpAddresses());
        assertEquals(TEST_DOMAIN, result.getRecord().getDomain());
    }

    @Test
    void createHost_unauthenticated() {
        mockUnauthenticated();

        HostResult result = hostService.createHost(TEST_HOSTNAME, TEST_IPS);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required. Please use loginWithDevice to authenticate.", result.getMessage());
        assertNull(result.getRecord());
        verifyNoInteractions(backendClient);
    }

    @Test
    void createHost_backendException() {
        mockAuthenticated();
        when(backendClient.createHost(any(HostCreateRequest.class), eq(BEARER_TOKEN)))
                .thenThrow(new RuntimeException("Duplicate hostname"));

        HostResult result = hostService.createHost(TEST_HOSTNAME, TEST_IPS);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Duplicate hostname"));
        assertNull(result.getRecord());
    }

    // ===== getHostsForDomain tests =====

    @Test
    void getHostsForDomain_success() {
        mockAuthenticated();
        List<HostRecord> hosts = Arrays.asList(
                new HostRecord("ns1.example.com", Arrays.asList("192.0.2.1"), TEST_DOMAIN, "2026-01-01T00:00:00Z"),
                new HostRecord("ns2.example.com", Arrays.asList("198.51.100.1"), TEST_DOMAIN, "2026-01-01T00:00:00Z")
        );
        when(backendClient.getHostsForDomain(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(hosts);

        HostListResult result = hostService.getHostsForDomain(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals("Host records retrieved successfully", result.getMessage());
        assertNotNull(result.getHosts());
        assertEquals(2, result.getHosts().size());
        assertEquals("ns1.example.com", result.getHosts().get(0).getHostname());
        assertEquals("ns2.example.com", result.getHosts().get(1).getHostname());
    }

    @Test
    void getHostsForDomain_unauthenticated() {
        mockUnauthenticated();

        HostListResult result = hostService.getHostsForDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required. Please use loginWithDevice to authenticate.", result.getMessage());
        assertNull(result.getHosts());
        verifyNoInteractions(backendClient);
    }

    @Test
    void getHostsForDomain_backendException() {
        mockAuthenticated();
        when(backendClient.getHostsForDomain(TEST_DOMAIN, BEARER_TOKEN))
                .thenThrow(new RuntimeException("Service unavailable"));

        HostListResult result = hostService.getHostsForDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Service unavailable"));
        assertNull(result.getHosts());
    }

    // ===== deleteHost tests =====

    @Test
    void deleteHost_success() {
        mockAuthenticated();
        HostActionResponse response = new HostActionResponse(true, "Host record deleted successfully");
        when(backendClient.deleteHost(TEST_HOSTNAME, BEARER_TOKEN)).thenReturn(response);

        HostActionResult result = hostService.deleteHost(TEST_HOSTNAME);

        assertTrue(result.isSuccess());
        assertEquals("Host record deleted successfully", result.getMessage());
    }

    @Test
    void deleteHost_unauthenticated() {
        mockUnauthenticated();

        HostActionResult result = hostService.deleteHost(TEST_HOSTNAME);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required. Please use loginWithDevice to authenticate.", result.getMessage());
        verifyNoInteractions(backendClient);
    }

    @Test
    void deleteHost_backendException() {
        mockAuthenticated();
        when(backendClient.deleteHost(TEST_HOSTNAME, BEARER_TOKEN))
                .thenThrow(new RuntimeException("Host not found"));

        HostActionResult result = hostService.deleteHost(TEST_HOSTNAME);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Host not found"));
    }
}
