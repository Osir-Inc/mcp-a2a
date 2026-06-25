package com.osir.mcp.services;

import com.osir.mcp.clients.ContactBackendClient;
import com.osir.mcp.models.contact.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ContactServiceTest {

    @Mock
    ContactBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    ContactService contactService;

    private static final String TEST_TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void listContacts_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        ContactDetail c = new ContactDetail();
        c.setId(1L);
        c.setFirstName("John");
        var contactResponse = new com.osir.mcp.models.contact.ContactListApiResponse();
        contactResponse.setData(List.of(c));
        when(backendClient.listContacts(null, TEST_TOKEN)).thenReturn(contactResponse);

        ContactListResult result = contactService.listContacts(null);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getContacts().size());
    }

    @Test
    void listContacts_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.listContacts(null).isSuccess());
    }

    @Test
    void getContact_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        ContactDetail c = new ContactDetail();
        c.setId(1L);
        when(backendClient.getContact("c-1", TEST_TOKEN)).thenReturn(c);

        ContactDetailResult result = contactService.getContact("c-1");

        assertTrue(result.isSuccess());
        assertEquals("1", result.getContact().getId());
    }

    @Test
    void getContact_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.getContact("c-1").isSuccess());
    }

    @Test
    void createContact_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        ContactDetail created = new ContactDetail();
        created.setId(99L);
        created.setFirstName("John");
        when(backendClient.createContact(any(ContactCreateRequest.class), eq(TEST_TOKEN))).thenReturn(created);

        ContactResult result = contactService.createContact("John", "Doe", "john@example.com", "+1.5551234567",
                null, "123 Main St", null, "Springfield", "IL", "62701", "US");

        assertTrue(result.isSuccess());
        assertEquals("99", result.getContact().getId());
    }

    @Test
    void createContact_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.createContact("John", "Doe", "john@example.com", "+1.5551234567",
                null, "123 Main St", null, "Springfield", "IL", "62701", "US").isSuccess());
    }

    @Test
    void updateContact_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        ContactDetail updated = new ContactDetail();
        updated.setId(1L);
        updated.setFirstName("Jane");
        when(backendClient.updateContact(eq("c-1"), any(ContactCreateRequest.class), eq(TEST_TOKEN))).thenReturn(updated);

        ContactResult result = contactService.updateContact("c-1", "Jane", null, null, null,
                null, null, null, null, null, null, null);

        assertTrue(result.isSuccess());
        assertEquals("Jane", result.getContact().getFirstName());
    }

    @Test
    void updateContact_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.updateContact("c-1", "Jane", null, null, null,
                null, null, null, null, null, null, null).isSuccess());
    }

    @Test
    void deleteContact_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        ContactActionResponse response = new ContactActionResponse();
        response.setSuccess(true);
        when(backendClient.deleteContact("c-1", TEST_TOKEN)).thenReturn(response);

        ContactActionResult result = contactService.deleteContact("c-1");

        assertTrue(result.isSuccess());
    }

    @Test
    void deleteContact_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.deleteContact("c-1").isSuccess());
    }

    @Test
    void getContactsForDomain_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        DomainContactsResult response = new DomainContactsResult();
        ContactDetail registrant = new ContactDetail();
        registrant.setFirstName("John");
        response.setRegistrant(registrant);
        when(backendClient.getContactsForDomain("dom-1", TEST_TOKEN)).thenReturn(response);

        DomainContactsResult result = contactService.getContactsForDomain("dom-1");

        assertTrue(result.isSuccess());
        assertNotNull(result.getRegistrant());
    }

    @Test
    void getContactsForDomain_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);
        assertFalse(contactService.getContactsForDomain("dom-1").isSuccess());
    }
}
