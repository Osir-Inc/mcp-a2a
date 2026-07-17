package com.osir.mcp.services;

import com.osir.mcp.clients.DnsBackendClient;
import com.osir.mcp.models.dns.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DnsServiceTest {

    @Mock
    DnsBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    DnsService dnsService;

    private static final String TEST_TOKEN = "Bearer test-token";
    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_RECORD_ID = "rec-1";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== listRecords =====

    @Test
    void listRecords_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        DnsRecord record = new DnsRecord();
        record.setId(TEST_RECORD_ID);
        record.setName("www");
        record.setType("A");
        record.setContent("93.184.216.34");
        when(backendClient.listDnsRecords(TEST_DOMAIN, TEST_TOKEN)).thenReturn(List.of(record));

        DnsRecordListResult result = dnsService.listRecords(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals(1, result.getRecords().size());
        assertEquals("A", result.getRecords().get(0).getType());
    }

    @Test
    void listRecords_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DnsRecordListResult result = dnsService.listRecords(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void listRecords_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.listDnsRecords(TEST_DOMAIN, TEST_TOKEN)).thenThrow(new RuntimeException("Timeout"));

        DnsRecordListResult result = dnsService.listRecords(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
    }

    // ===== createRecord =====

    @Test
    void createRecord_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        DnsRecord created = new DnsRecord();
        created.setId("rec-new");
        created.setName("www");
        created.setType("A");
        created.setContent("93.184.216.34");
        when(backendClient.createDnsRecord(eq(TEST_DOMAIN), any(DnsRecordRequest.class), eq(TEST_TOKEN)))
                .thenReturn(created);

        DnsRecordResult result = dnsService.createRecord(TEST_DOMAIN, "www", "A", "93.184.216.34", null, null);

        assertTrue(result.isSuccess());
        assertNotNull(result.getRecord());
        assertEquals("rec-new", result.getRecord().getId());
    }

    @Test
    void createRecord_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DnsRecordResult result = dnsService.createRecord(TEST_DOMAIN, "www", "A", "93.184.216.34", null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== updateRecord =====

    @Test
    void updateRecord_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        DnsRecord updated = new DnsRecord();
        updated.setId(TEST_RECORD_ID);
        updated.setContent("1.2.3.4");
        when(backendClient.updateDnsRecord(eq(TEST_DOMAIN), eq(TEST_RECORD_ID), any(DnsRecordRequest.class), eq(TEST_TOKEN)))
                .thenReturn(updated);

        DnsRecordResult result = dnsService.updateRecord(TEST_DOMAIN, TEST_RECORD_ID, null, null, "1.2.3.4", null, null);

        assertTrue(result.isSuccess());
        assertEquals("1.2.3.4", result.getRecord().getContent());
    }

    @Test
    void updateRecord_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DnsRecordResult result = dnsService.updateRecord(TEST_DOMAIN, TEST_RECORD_ID, null, null, "1.2.3.4", null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== deleteRecord =====

    @Test
    void deleteRecord_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        // deleteDnsRecord returns void — Mockito leaves it as a no-op (no exception = success)
        DnsActionResult result = dnsService.deleteRecord(TEST_DOMAIN, TEST_RECORD_ID);

        assertTrue(result.isSuccess());
    }

    @Test
    void deleteRecord_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DnsActionResult result = dnsService.deleteRecord(TEST_DOMAIN, TEST_RECORD_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== getRecord =====

    @Test
    void getRecord_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        DnsRecord record = new DnsRecord();
        record.setId(TEST_RECORD_ID);
        record.setName("www");
        record.setType("A");
        when(backendClient.getDnsRecord(TEST_DOMAIN, TEST_RECORD_ID, TEST_TOKEN)).thenReturn(record);

        DnsRecordResult result = dnsService.getRecord(TEST_DOMAIN, TEST_RECORD_ID);

        assertTrue(result.isSuccess());
        assertEquals(TEST_RECORD_ID, result.getRecord().getId());
    }

    @Test
    void getRecord_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        DnsRecordResult result = dnsService.getRecord(TEST_DOMAIN, TEST_RECORD_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }
}
