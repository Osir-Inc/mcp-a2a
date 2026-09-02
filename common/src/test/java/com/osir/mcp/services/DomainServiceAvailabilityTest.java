package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.DomainAvailabilityResult;
import com.osir.mcp.models.domain.DomainAvailabilityResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Audit F1 regression: availability answers WITHOUT authentication (public catalog endpoint)
 * and never fabricates available:false for a non-domain reason.
 */
class DomainServiceAvailabilityTest {

    @Mock
    DomainBackendClient backendClient;

    @Mock
    AuthService authService;

    @Mock
    TransferService transferService;

    @InjectMocks
    DomainService domainService;

    private static final String DOMAIN = "example.com";
    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private DomainAvailabilityResponse availableResponse() {
        DomainAvailabilityResponse response = new DomainAvailabilityResponse();
        response.setDomain(DOMAIN);
        response.setAvailable(true);
        response.setPriceCents(1039);
        response.setCurrency("USD");
        return response;
    }

    @Test
    void anonymous_usesPublicEndpoint_andReportsAvailable() {
        when(authService.isAuthenticated()).thenReturn(false);
        when(backendClient.checkAvailabilityPublic(DOMAIN)).thenReturn(availableResponse());

        DomainAvailabilityResult result = domainService.checkAvailability(DOMAIN);

        assertTrue(result.isAvailable());
        assertEquals(10.39, result.getPrice());
        assertEquals("USD", result.getCurrency());
        verify(backendClient, never()).checkAvailability(anyString(), anyString());
    }

    @Test
    void authenticated_usesAccountEndpoint() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TOKEN);
        when(backendClient.checkAvailability(DOMAIN, TOKEN)).thenReturn(availableResponse());

        DomainAvailabilityResult result = domainService.checkAvailability(DOMAIN);

        assertTrue(result.isAvailable());
        verify(backendClient, never()).checkAvailabilityPublic(anyString());
    }

    @Test
    void backendError_propagates_neverFabricatesUnavailable() {
        when(authService.isAuthenticated()).thenReturn(false);
        when(backendClient.checkAvailabilityPublic(DOMAIN)).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> domainService.checkAvailability(DOMAIN));
    }

    @Test
    void takenDomain_reportsBackendReason() {
        DomainAvailabilityResponse response = new DomainAvailabilityResponse();
        response.setDomain(DOMAIN);
        response.setAvailable(false);
        response.setReason("Domain is already registered");
        when(authService.isAuthenticated()).thenReturn(false);
        when(backendClient.checkAvailabilityPublic(DOMAIN)).thenReturn(response);

        DomainAvailabilityResult result = domainService.checkAvailability(DOMAIN);

        assertFalse(result.isAvailable());
        assertEquals("Domain is already registered", result.getMessage());
    }
}
