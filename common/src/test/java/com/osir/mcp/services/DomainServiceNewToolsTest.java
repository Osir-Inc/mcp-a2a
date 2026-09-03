package com.osir.mcp.services;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DomainServiceNewToolsTest {

    @Mock
    DomainBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    DomainService domainService;

    private static final String TEST_DOMAIN = "example.com";
    private static final String BEARER_TOKEN = "Bearer test-token";

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

    // ===== renewDomain tests =====

    @Test
    void renewDomain_success() {
        mockAuthenticated();
        DomainRenewalResponse response = new DomainRenewalResponse(TEST_DOMAIN, true, "renewed");
        response.setMessage("Domain renewed for 2 years");
        when(backendClient.renewDomain(eq(TEST_DOMAIN), any(DomainRenewalRequest.class), eq(BEARER_TOKEN)))
                .thenReturn(response);

        DomainRenewalResult result = domainService.renewDomain(TEST_DOMAIN, 2);

        assertTrue(result.isSuccess());
        assertEquals("Domain renewed for 2 years", result.getMessage());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("renewed", result.getStatus());
    }

    @Test
    void renewDomain_unauthenticated() {
        mockUnauthenticated();

        DomainRenewalResult result = domainService.renewDomain(TEST_DOMAIN, 2);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required", result.getMessage());
        assertEquals(TEST_DOMAIN, result.getDomain());
        verifyNoInteractions(backendClient);
    }

    @Test
    void renewDomain_backendException() {
        mockAuthenticated();
        when(backendClient.renewDomain(eq(TEST_DOMAIN), any(DomainRenewalRequest.class), eq(BEARER_TOKEN)))
                .thenThrow(new RuntimeException("Connection refused"));

        DomainRenewalResult result = domainService.renewDomain(TEST_DOMAIN, 1);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertNull(result.getStatus());
    }

    /** Backend v2 envelope: payload nested under data (BACKEND-CONTRACT-lock-and-info.md). */
    private static DomainLockResponse lockEnvelope(String domain, boolean locked, String message) {
        DomainLockResponse response = new DomainLockResponse();
        response.setSuccess(true);
        DomainLockResponse.Data data = new DomainLockResponse.Data();
        data.setDomain(domain);
        data.setLocked(locked);
        data.setMessage(message);
        response.setData(data);
        return response;
    }

    // ===== getDomainInfo contract mapping (BACKEND-CONTRACT-lock-and-info.md) =====

    private static DomainInfoBackendResponse infoEnvelope(String status) {
        DomainInfoBackendResponse response = new DomainInfoBackendResponse();
        response.setDomain(TEST_DOMAIN);
        response.setSuccess(true);
        DomainInfoBackendResponse.DomainData data = new DomainInfoBackendResponse.DomainData();
        data.setStatus(status);
        data.setAutoRenew(true);
        data.setPrivacy(true);          // backend field name is "privacy"
        data.setLocked(true);
        data.setCreationDate("2024-01-01T00:00:00Z");
        data.setExpiryDate("2027-01-01T00:00:00Z");
        response.setData(data);
        return response;
    }

    @Test
    void getDomainInfo_mapsContractFields() {
        mockAuthenticated();
        when(backendClient.getDomainInfo(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(infoEnvelope("active"));

        DomainInfoResult result = domainService.getDomainInfo(TEST_DOMAIN);

        assertEquals(Boolean.TRUE, result.getAutoRenew());
        assertEquals(Boolean.TRUE, result.getPrivacyProtection());
        assertEquals(Boolean.TRUE, result.getLocked());
        assertEquals("2024-01-01T00:00:00Z", result.getRegistrationDate());
        assertEquals("2027-01-01T00:00:00Z", result.getExpirationDate());
    }

    @Test
    void getDomainInfo_transferredOut_suppressesMeaninglessAutoRenew() {
        mockAuthenticated();
        when(backendClient.getDomainInfo(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(infoEnvelope("transferredOut"));

        DomainInfoResult result = domainService.getDomainInfo(TEST_DOMAIN);

        assertNull(result.getAutoRenew());                       // backend hardcodes false here
        assertEquals(Boolean.TRUE, result.getPrivacyProtection()); // privacy stays real
    }

    // ===== lockDomain tests =====

    @Test
    void lockDomain_success() {
        mockAuthenticated();
        DomainLockResponse response = lockEnvelope(TEST_DOMAIN, true, "Domain locked successfully");
        when(backendClient.lockDomain(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(response);

        DomainActionResult result = domainService.lockDomain(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals("Domain locked successfully", result.getMessage());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("locked", result.getStatus());
    }

    @Test
    void lockDomain_nullBodyFields_fallsBackToDeterministicResult() {
        // Regression: backend 200 with unmapped/empty body must never yield success:true + all-null.
        mockAuthenticated();
        when(backendClient.lockDomain(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(new DomainLockResponse());

        DomainActionResult result = domainService.lockDomain(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("locked", result.getStatus());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains(TEST_DOMAIN));
    }

    @Test
    void unlockDomain_nullBodyFields_fallsBackToDeterministicResult() {
        mockAuthenticated();
        when(backendClient.unlockDomain(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(new DomainLockResponse());

        DomainActionResult result = domainService.unlockDomain(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals("unlocked", result.getStatus());
        assertTrue(result.getMessage().contains(TEST_DOMAIN));
    }

    @Test
    void lockDomain_unauthenticated() {
        mockUnauthenticated();

        DomainActionResult result = domainService.lockDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required", result.getMessage());
        verifyNoInteractions(backendClient);
    }

    @Test
    void lockDomain_backendException() {
        mockAuthenticated();
        when(backendClient.lockDomain(TEST_DOMAIN, BEARER_TOKEN))
                .thenThrow(new RuntimeException("Service unavailable"));

        DomainActionResult result = domainService.lockDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Service unavailable"));
        assertNull(result.getStatus());
    }

    // ===== unlockDomain tests =====

    @Test
    void unlockDomain_success() {
        mockAuthenticated();
        DomainLockResponse response = lockEnvelope(TEST_DOMAIN, false, "Domain unlocked successfully");
        when(backendClient.unlockDomain(TEST_DOMAIN, BEARER_TOKEN)).thenReturn(response);

        DomainActionResult result = domainService.unlockDomain(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertEquals("Domain unlocked successfully", result.getMessage());
        assertEquals(TEST_DOMAIN, result.getDomain());
        assertEquals("unlocked", result.getStatus());
    }

    @Test
    void unlockDomain_unauthenticated() {
        mockUnauthenticated();

        DomainActionResult result = domainService.unlockDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required", result.getMessage());
        verifyNoInteractions(backendClient);
    }

    @Test
    void unlockDomain_backendException() {
        mockAuthenticated();
        when(backendClient.unlockDomain(TEST_DOMAIN, BEARER_TOKEN))
                .thenThrow(new RuntimeException("Timeout"));

        DomainActionResult result = domainService.unlockDomain(TEST_DOMAIN);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
    }

    // ===== updateAutoRenew tests =====

    @Test
    void updateAutoRenew_enable_success() {
        mockAuthenticated();
        AutoRenewResponse response = new AutoRenewResponse(TEST_DOMAIN, true, "auto_renew_enabled");
        response.setMessage("Auto-renew enabled");
        when(backendClient.enableAutoRenew(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenReturn(response);

        DomainActionResult result = domainService.updateAutoRenew(TEST_DOMAIN, true);

        assertTrue(result.isSuccess());
        assertEquals("Auto-renew enabled", result.getMessage());
        assertEquals("auto_renew_enabled", result.getStatus());
    }

    @Test
    void updateAutoRenew_disable_success() {
        mockAuthenticated();
        AutoRenewResponse response = new AutoRenewResponse(TEST_DOMAIN, true, "auto_renew_disabled");
        response.setMessage("Auto-renew disabled");
        when(backendClient.disableAutoRenew(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenReturn(response);

        DomainActionResult result = domainService.updateAutoRenew(TEST_DOMAIN, false);

        assertTrue(result.isSuccess());
        assertEquals("Auto-renew disabled", result.getMessage());
    }

    @Test
    void updateAutoRenew_unauthenticated() {
        mockUnauthenticated();

        DomainActionResult result = domainService.updateAutoRenew(TEST_DOMAIN, true);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required", result.getMessage());
        verifyNoInteractions(backendClient);
    }

    @Test
    void updateAutoRenew_backendException() {
        mockAuthenticated();
        when(backendClient.enableAutoRenew(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenThrow(new RuntimeException("Bad request"));

        DomainActionResult result = domainService.updateAutoRenew(TEST_DOMAIN, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Bad request"));
    }

    // ===== updatePrivacyProtection tests =====

    @Test
    void updatePrivacyProtection_enable_success() {
        mockAuthenticated();
        PrivacyResponse response = new PrivacyResponse(TEST_DOMAIN, true, "privacy_enabled");
        response.setMessage("Privacy protection enabled");
        when(backendClient.enablePrivacy(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenReturn(response);

        DomainActionResult result = domainService.updatePrivacyProtection(TEST_DOMAIN, true);

        assertTrue(result.isSuccess());
        assertEquals("Privacy protection enabled", result.getMessage());
        assertEquals("privacy_enabled", result.getStatus());
    }

    @Test
    void updatePrivacyProtection_disable_success() {
        mockAuthenticated();
        PrivacyResponse response = new PrivacyResponse(TEST_DOMAIN, false, "privacy_disabled");
        response.setMessage("Privacy protection disabled");
        when(backendClient.disablePrivacy(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenReturn(response);

        DomainActionResult result = domainService.updatePrivacyProtection(TEST_DOMAIN, false);

        assertTrue(result.isSuccess());
        assertEquals("Privacy protection disabled", result.getMessage());
    }

    @Test
    void updatePrivacyProtection_unauthenticated() {
        mockUnauthenticated();

        DomainActionResult result = domainService.updatePrivacyProtection(TEST_DOMAIN, true);

        assertFalse(result.isSuccess());
        assertEquals("Authentication required", result.getMessage());
        verifyNoInteractions(backendClient);
    }

    @Test
    void updatePrivacyProtection_backendException() {
        mockAuthenticated();
        when(backendClient.enablePrivacy(eq(TEST_DOMAIN), eq(BEARER_TOKEN)))
                .thenThrow(new RuntimeException("Internal server error"));

        DomainActionResult result = domainService.updatePrivacyProtection(TEST_DOMAIN, true);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Internal server error"));
    }
}
