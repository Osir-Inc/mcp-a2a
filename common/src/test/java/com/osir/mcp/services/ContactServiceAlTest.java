package com.osir.mcp.services;

import com.osir.mcp.clients.ContactBackendClient;
import com.osir.mcp.models.contact.ContactDetail;
import com.osir.mcp.models.contact.RegistryAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** .al orders must name a saved contact carrying the AKEP fields, else they land on the account default contact. */
class ContactServiceAlTest {

    @Mock ContactBackendClient backendClient;
    @Mock AuthService authService;
    @InjectMocks ContactService contactService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn("t");
    }

    private void savedContact(RegistryAttributes attrs) {
        ContactDetail c = new ContactDetail();
        c.setRegistryAttributes(attrs);
        when(backendClient.getContact("7360", "t")).thenReturn(c);
    }

    @Test
    void missingForAl_naturalPersonNeedsNoNipt_companyDoes() {
        assertEquals(List.of(), RegistryAttributes.missingForAl(new RegistryAttributes("Person Natyror", "op", "J75831039W", null)));
        assertEquals(List.of("nipt missing"), RegistryAttributes.missingForAl(new RegistryAttributes("Person Juridik Privat", "passport", "26HE11614", " ")));
        assertEquals(List.of("entityType missing", "doctype missing", "docnr missing", "nipt missing"), RegistryAttributes.missingForAl(null));
    }

    @Test
    void missingForAl_refusesValuesAkepDoesNotKnow() {
        List<String> p = RegistryAttributes.missingForAl(new RegistryAttributes("company", "id", "X1", "L1"));
        assertEquals(2, p.size());
        assertTrue(p.get(0).startsWith("entityType must be one of"));
        assertTrue(p.get(1).startsWith("doctype must be one of"));
    }

    @Test
    void isAl() {
        assertTrue(ContactService.isAl(" Example.AL "));
        assertFalse(ContactService.isAl("example.com.al.com"));
        assertFalse(ContactService.isAl(null));
    }

    @Test
    void noRegistrantId_isAProblem() {
        assertTrue(contactService.alRegistrantProblem(null, null).contains("registrantId"));
    }

    @Test
    void contactMissingNipt_namesIt() {
        savedContact(new RegistryAttributes("Person Juridik Privat", "passport", "26HE11614", null));
        assertTrue(contactService.alRegistrantProblem(7360L, null).contains("nipt"));
    }

    @Test
    void completeContact_ok() {
        savedContact(new RegistryAttributes("Person Natyror", "op", "J75831039W", null));
        assertNull(contactService.alRegistrantProblem(7360L, null));
    }

    @Test
    void completeOverride_winsOverBareContact() {
        savedContact(null);
        assertNull(contactService.alRegistrantProblem(7360L, new RegistryAttributes("Person Natyror", "op", "X1", null)));
    }

    @Test
    void partialOverride_judgedAlone_becauseBackendReplacesStoredFields() {
        savedContact(new RegistryAttributes("Person Juridik Privat", "passport", "26HE11614", null));
        String p = contactService.alRegistrantProblem(7360L, new RegistryAttributes(null, null, null, "L123"));
        assertTrue(p.startsWith("registryAttributes on this order"));
    }

    @Test
    void unknownContact_refusedEvenWithCompleteOverride() {
        when(backendClient.getContact(any(), any())).thenThrow(new RuntimeException("404"));
        assertTrue(contactService.alRegistrantProblem(999L, new RegistryAttributes("Person Natyror", "op", "X1", null))
                .contains("not found"));
    }
}
