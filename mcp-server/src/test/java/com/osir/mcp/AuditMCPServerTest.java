package com.osir.mcp;

import com.osir.mcp.models.audit.AuditLogListResult;
import com.osir.mcp.models.audit.AuditTrailResult;
import com.osir.mcp.models.audit.RecentActivityResult;
import com.osir.mcp.services.AuditService;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuditMCPServerTest {

    @Mock
    AuditService auditService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    AuditMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getDomainAuditTrail_delegatesToService() {
        AuditTrailResult expected = new AuditTrailResult(true, "OK");
        when(auditService.getDomainAuditTrail("example.com")).thenReturn(expected);
        assertSame(expected, mcpServer.getDomainAuditTrail("example.com", mockConnection));
    }

    @Test
    void getDomainAuditTrail_handlesException() {
        when(auditService.getDomainAuditTrail("example.com")).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getDomainAuditTrail("example.com", mockConnection).isSuccess());
    }

    @Test
    void getMyAuditLogs_delegatesToService() {
        AuditLogListResult expected = new AuditLogListResult(true, "OK");
        when(auditService.getMyAuditLogs(null, null)).thenReturn(expected);
        assertSame(expected, mcpServer.getMyAuditLogs(null, null, mockConnection));
    }

    @Test
    void getMyAuditLogs_handlesException() {
        when(auditService.getMyAuditLogs(null, null)).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getMyAuditLogs(null, null, mockConnection).isSuccess());
    }

    @Test
    void getRecentActivity_delegatesToService() {
        RecentActivityResult expected = new RecentActivityResult(true, "OK");
        when(auditService.getRecentActivity()).thenReturn(expected);
        assertSame(expected, mcpServer.getRecentActivity(mockConnection));
    }

    @Test
    void getRecentActivity_handlesException() {
        when(auditService.getRecentActivity()).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getRecentActivity(mockConnection).isSuccess());
    }
}
