package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.contact.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.ContactService;
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

class ContactMCPServerTest {

    @Mock
    ContactService contactService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    ContactMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    @Test
    void listContacts_delegatesToService() {
        ContactListResult expected = new ContactListResult(true, "OK");
        when(contactService.listContacts(null)).thenReturn(expected);
        assertSame(expected, mcpServer.listContacts(null, mockConnection));
    }

    @Test
    void listContacts_handlesException() {
        when(contactService.listContacts(null)).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.listContacts(null, mockConnection).isSuccess());
    }

    @Test
    void getContact_delegatesToService() {
        ContactDetailResult expected = new ContactDetailResult(true, "OK");
        when(contactService.getContact("c-1")).thenReturn(expected);
        assertSame(expected, mcpServer.getContact("c-1", mockConnection));
    }

    @Test
    void getContact_handlesException() {
        when(contactService.getContact("c-1")).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getContact("c-1", mockConnection).isSuccess());
    }

    @Test
    void createContact_delegatesToService() {
        ContactResult expected = new ContactResult(true, "Created");
        when(contactService.createContact("John", "Doe", "j@e.com", "+1.555", null, "123 St", null, "City", null, "12345", "US"))
                .thenReturn(expected);
        assertSame(expected, mcpServer.createContact("John", "Doe", "j@e.com", "+1.555", null, "123 St", null, "City", null, "12345", "US", mockConnection));
    }

    @Test
    void createContact_handlesException() {
        when(contactService.createContact("John", "Doe", "j@e.com", "+1.555", null, "123 St", null, "City", null, "12345", "US"))
                .thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.createContact("John", "Doe", "j@e.com", "+1.555", null, "123 St", null, "City", null, "12345", "US", mockConnection).isSuccess());
    }

    @Test
    void updateContact_delegatesToService() {
        ContactResult expected = new ContactResult(true, "Updated");
        when(contactService.updateContact("c-1", "Jane", null, null, null, null, null, null, null, null, null, null))
                .thenReturn(expected);
        assertSame(expected, mcpServer.updateContact("c-1", "Jane", null, null, null, null, null, null, null, null, null, null, mockConnection));
    }

    @Test
    void updateContact_handlesException() {
        when(contactService.updateContact("c-1", "Jane", null, null, null, null, null, null, null, null, null, null))
                .thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.updateContact("c-1", "Jane", null, null, null, null, null, null, null, null, null, null, mockConnection).isSuccess());
    }

    @Test
    void deleteContact_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "deleteContact", "summary");
        when(pendingActionStore.stage(eq("deleteContact"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.deleteContact("c-1", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("deleteContact"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    @Test
    void getContactsForDomain_delegatesToService() {
        DomainContactsResult expected = new DomainContactsResult(true, "OK");
        when(contactService.getContactsForDomain("dom-1")).thenReturn(expected);
        assertSame(expected, mcpServer.getContactsForDomain("dom-1", mockConnection));
    }

    @Test
    void getContactsForDomain_handlesException() {
        when(contactService.getContactsForDomain("dom-1")).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getContactsForDomain("dom-1", mockConnection).isSuccess());
    }
}
