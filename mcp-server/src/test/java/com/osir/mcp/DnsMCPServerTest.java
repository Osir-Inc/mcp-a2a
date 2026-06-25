package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.dns.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.DnsService;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class DnsMCPServerTest {

    @Mock
    DnsService dnsService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    DnsMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    @Test
    void listDnsRecords_delegatesToService() {
        DnsRecordListResult expected = new DnsRecordListResult(true, "OK");
        when(dnsService.listRecords("example.com")).thenReturn(expected);

        DnsRecordListResult result = mcpServer.listDnsRecords("example.com", mockConnection);

        assertSame(expected, result);
        verify(dnsService).listRecords("example.com");
    }

    @Test
    void listDnsRecords_handlesException() {
        when(dnsService.listRecords("example.com")).thenThrow(new RuntimeException("Fail"));

        DnsRecordListResult result = mcpServer.listDnsRecords("example.com", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    @Test
    void createDnsRecord_delegatesToService() {
        DnsRecordResult expected = new DnsRecordResult(true, "Created");
        when(dnsService.createRecord("example.com", "www", "A", "1.2.3.4", null, null)).thenReturn(expected);

        DnsRecordResult result = mcpServer.createDnsRecord("example.com", "www", "A", "1.2.3.4", null, null, mockConnection);

        assertSame(expected, result);
    }

    @Test
    void createDnsRecord_handlesException() {
        when(dnsService.createRecord("example.com", "www", "A", "1.2.3.4", null, null))
                .thenThrow(new RuntimeException("Fail"));

        DnsRecordResult result = mcpServer.createDnsRecord("example.com", "www", "A", "1.2.3.4", null, null, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    @Test
    void updateDnsRecord_delegatesToService() {
        DnsRecordResult expected = new DnsRecordResult(true, "Updated");
        when(dnsService.updateRecord("example.com", "rec-1", null, null, "1.2.3.4", null, null)).thenReturn(expected);

        DnsRecordResult result = mcpServer.updateDnsRecord("example.com", "rec-1", null, null, "1.2.3.4", null, null, mockConnection);

        assertSame(expected, result);
    }

    @Test
    void updateDnsRecord_handlesException() {
        when(dnsService.updateRecord("example.com", "rec-1", null, null, "1.2.3.4", null, null))
                .thenThrow(new RuntimeException("Fail"));

        DnsRecordResult result = mcpServer.updateDnsRecord("example.com", "rec-1", null, null, "1.2.3.4", null, null, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    @Test
    void deleteDnsRecord_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "deleteDnsRecord", "summary");
        when(pendingActionStore.stage(eq("deleteDnsRecord"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.deleteDnsRecord("example.com", "rec-1", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("deleteDnsRecord"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    @Test
    void getDnsRecord_delegatesToService() {
        DnsRecordResult expected = new DnsRecordResult(true, "OK");
        when(dnsService.getRecord("example.com", "rec-1")).thenReturn(expected);

        DnsRecordResult result = mcpServer.getDnsRecord("example.com", "rec-1", mockConnection);

        assertSame(expected, result);
    }

    @Test
    void getDnsRecord_handlesException() {
        when(dnsService.getRecord("example.com", "rec-1")).thenThrow(new RuntimeException("Fail"));

        DnsRecordResult result = mcpServer.getDnsRecord("example.com", "rec-1", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
