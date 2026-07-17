package com.osir.mcp.services;

import com.osir.mcp.clients.AuditBackendClient;
import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.account.UserProfile;
import com.osir.mcp.models.audit.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditServiceTest {

    @Mock
    AuditBackendClient auditBackendClient;

    @Mock
    DomainBackendClient domainBackendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    AuditService auditService;

    private static final String TEST_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getDomainAuditTrail_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        AuditEntry entry = new AuditEntry();
        entry.setAction("DOMAIN_CREATE");
        entry.setActor("admin");
        entry.setActorType("CUSTOMER");
        entry.setTimestamp("2026-02-19T10:00:00Z");
        entry.setDetails("Domain created");
        entry.setWasSuccessful(true);
        var auditResponse = new com.osir.mcp.models.audit.AuditLogListResponse();
        auditResponse.setEntries(List.of(entry));
        auditResponse.setTotalCount(1);
        when(auditBackendClient.getDomainAuditTrail("example.com", TEST_TOKEN)).thenReturn(auditResponse);

        AuditTrailResult result = auditService.getDomainAuditTrail("example.com");

        assertTrue(result.isSuccess());
        assertEquals("example.com", result.getDomain());
        assertEquals(1, result.getEntries().size());
        assertEquals("DOMAIN_CREATE", result.getEntries().get(0).getAction());
    }

    @Test
    void getDomainAuditTrail_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(auditService.getDomainAuditTrail("example.com").isSuccess());
    }

    @Test
    void getDomainAuditTrail_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(auditBackendClient.getDomainAuditTrail("example.com", TEST_TOKEN))
                .thenThrow(new RuntimeException("Backend error"));

        assertFalse(auditService.getDomainAuditTrail("example.com").isSuccess());
    }

    @Test
    void getMyAuditLogs_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        UserProfile profile = new UserProfile();
        profile.setCustomerId("cust-1");
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenReturn(profile);

        AuditLogListResponse response = new AuditLogListResponse();
        AuditEntry entry = new AuditEntry();
        entry.setAction("LOGIN");
        response.setEntries(List.of(entry));
        response.setTotalCount(1);
        response.setPage(0);
        response.setSize(20);
        when(auditBackendClient.getCustomerAuditLogs("cust-1", null, null, TEST_TOKEN)).thenReturn(response);

        AuditLogListResult result = auditService.getMyAuditLogs(null, null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getEntries().size());
        assertEquals(1, result.getTotalCount());
    }

    @Test
    void getMyAuditLogs_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(auditService.getMyAuditLogs(null, null).isSuccess());
    }

    @Test
    void getMyAuditLogs_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        UserProfile profile = new UserProfile();
        profile.setCustomerId("cust-1");
        when(domainBackendClient.getMyProfile(TEST_TOKEN)).thenReturn(profile);
        when(auditBackendClient.getCustomerAuditLogs("cust-1", null, null, TEST_TOKEN))
                .thenThrow(new RuntimeException("Backend error"));

        assertFalse(auditService.getMyAuditLogs(null, null).isSuccess());
    }

    @Test
    void getRecentActivity_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        AuditEntry entry = new AuditEntry();
        entry.setAction("NAMESERVER_UPDATE");
        when(auditBackendClient.getRecentActivity(TEST_TOKEN)).thenReturn(List.of(entry));

        RecentActivityResult result = auditService.getRecentActivity();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getActivities().size());
        assertEquals("NAMESERVER_UPDATE", result.getActivities().get(0).getAction());
    }

    @Test
    void getRecentActivity_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(auditService.getRecentActivity().isSuccess());
    }

    @Test
    void getRecentActivity_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(auditBackendClient.getRecentActivity(TEST_TOKEN)).thenThrow(new RuntimeException("Backend error"));

        assertFalse(auditService.getRecentActivity().isSuccess());
    }
}
