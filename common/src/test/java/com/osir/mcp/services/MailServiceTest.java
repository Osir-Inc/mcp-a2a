package com.osir.mcp.services;

import com.osir.mcp.clients.MailBackendClient;
import com.osir.mcp.models.mail.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MailServiceTest {

    @Mock
    MailBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    MailService mailService;

    private static final String TEST_TOKEN = "Bearer test-token";
    private static final String TEST_DOMAIN = "example.com";
    private static final String TEST_PACKAGE_ID = "11111111-2222-3333-4444-555555555555";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private void authenticated() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
    }

    /**
     * Build this BEFORE the outer when(...): stubbing a mock inside another when()'s argument
     * evaluation trips Mockito's UnfinishedStubbingException. Real WebApplicationException, not a
     * mocked Throwable — rendering a mocked Throwable's stack trace hangs the test JVM.
     */
    private jakarta.ws.rs.WebApplicationException backendError(int status, String json) {
        jakarta.ws.rs.core.Response response = mock(jakarta.ws.rs.core.Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn(json);
        when(response.getStatusInfo()).thenReturn(jakarta.ws.rs.core.Response.Status.fromStatusCode(status));
        return new jakarta.ws.rs.WebApplicationException(response);
    }

    // ===== auth gate =====

    @Test
    void allOps_requireAuthentication() {
        when(authService.isAuthenticated()).thenReturn(false);

        assertFalse(mailService.listPlans().isSuccess());
        assertFalse(mailService.getQuote(TEST_PACKAGE_ID, "ANNUAL").isSuccess());
        assertFalse(mailService.enableDomain(TEST_DOMAIN, null, null, null).isSuccess());
        assertFalse(mailService.listDomains().isSuccess());
        assertFalse(mailService.getDnsRecords(TEST_DOMAIN).isSuccess());
        assertFalse(mailService.verifyDns(TEST_DOMAIN).isSuccess());
        assertFalse(mailService.createMailbox(TEST_DOMAIN, "info", TEST_PACKAGE_ID, "ANNUAL").isSuccess());
        assertFalse(mailService.listMailboxes().isSuccess());
        assertFalse(mailService.setMailboxPassword("mb-1", "pw").isSuccess());
        assertFalse(mailService.deleteMailbox("mb-1").isSuccess());
        assertFalse(mailService.getUsage().isSuccess());
        verifyNoInteractions(backendClient);
    }

    // ===== plans =====

    @Test
    void listPlans_success() {
        authenticated();
        MailPlan plan = new MailPlan();
        plan.setPackageId(TEST_PACKAGE_ID);
        plan.setName("Basic");
        plan.setMonthlyCents(199);
        when(backendClient.getPlans(TEST_TOKEN)).thenReturn(List.of(plan));

        MailPlanListResult result = mailService.listPlans();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getPlans().size());
        assertEquals("Basic", result.getPlans().get(0).getName());
    }

    @Test
    void listPlans_featureFlagOff_reportsNotAvailable() {
        authenticated();
        jakarta.ws.rs.WebApplicationException ex =
                backendError(400, "{\"error\":\"Email hosting is not enabled\"}");
        when(backendClient.getPlans(TEST_TOKEN)).thenThrow(ex);

        MailPlanListResult result = mailService.listPlans();

        assertFalse(result.isSuccess());
        assertEquals("Email hosting is not available on this platform.", result.getMessage());
    }

    // ===== quote =====

    @Test
    void getQuote_normalizesTermAndFillsSuccess() {
        authenticated();
        MailQuoteResult apiResult = new MailQuoteResult();
        apiResult.setPriceCents(1999);
        when(backendClient.getQuote(eq(TEST_PACKAGE_ID), eq("ANNUAL"), eq(TEST_TOKEN))).thenReturn(apiResult);

        MailQuoteResult result = mailService.getQuote(TEST_PACKAGE_ID, "annual");

        assertTrue(result.isSuccess());
        assertEquals(1999, result.getPriceCents());
        verify(backendClient).getQuote(TEST_PACKAGE_ID, "ANNUAL", TEST_TOKEN);
    }

    // ===== enableDomain =====

    @Test
    void enableDomain_pendingDns_tellsUserToPublishRecords() {
        authenticated();
        MailDomainEnableResult apiResult = new MailDomainEnableResult();
        MailDomainInfo domain = new MailDomainInfo();
        domain.setFqdn(TEST_DOMAIN);
        domain.setStatus("PENDING_DNS");
        apiResult.setDomain(domain);
        when(backendClient.enableDomain(eq(TEST_DOMAIN), any(), eq(TEST_TOKEN))).thenReturn(apiResult);

        MailDomainEnableResult result = mailService.enableDomain(TEST_DOMAIN, "external_manual", null, null);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("verifyMailDns"));
    }

    @Test
    void enableDomain_conflict409_explainsConfirmFlags() {
        authenticated();
        jakarta.ws.rs.WebApplicationException ex =
                backendError(409, "{\"error\":\"Existing MX record points elsewhere\"}");
        when(backendClient.enableDomain(eq(TEST_DOMAIN), any(), eq(TEST_TOKEN))).thenThrow(ex);

        MailDomainEnableResult result = mailService.enableDomain(TEST_DOMAIN, null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Existing MX record points elsewhere"));
        assertTrue(result.getMessage().contains("takeoverConfirmed"));
    }

    // ===== verifyDns =====

    @Test
    void verifyDns_missingRecords_saysStillMissing() {
        authenticated();
        MailDnsVerifyResult apiResult = new MailDnsVerifyResult();
        apiResult.setVerified(false);
        MailDnsRecordInfo missing = new MailDnsRecordInfo();
        missing.setType("MX");
        apiResult.setMissing(List.of(missing));
        when(backendClient.verifyDns(TEST_DOMAIN, TEST_TOKEN)).thenReturn(apiResult);

        MailDnsVerifyResult result = mailService.verifyDns(TEST_DOMAIN);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("missing"));
        assertEquals(1, result.getMissing().size());
    }

    // ===== createMailbox =====

    @Test
    void createMailbox_success_passesPasswordThroughOnce() {
        authenticated();
        MailboxCreateResult apiResult = new MailboxCreateResult();
        MailAccountInfo account = new MailAccountInfo();
        account.setId("mb-1");
        account.setLocalPart("info");
        apiResult.setAccount(account);
        apiResult.setPassword("generated-secret");
        when(backendClient.createMailbox(eq(TEST_DOMAIN), any(), eq(TEST_TOKEN))).thenReturn(apiResult);

        MailboxCreateResult result = mailService.createMailbox(TEST_DOMAIN, "info", TEST_PACKAGE_ID, "monthly");

        assertTrue(result.isSuccess());
        assertEquals("generated-secret", result.getPassword());
        assertEquals("info@example.com", result.getEmailAddress());
        assertNotNull(result.getClientSettings());
        assertTrue(result.getMessage().contains("exactly once"));
    }

    @Test
    void createMailbox_insufficientBalance402_pointsToTopUp() {
        authenticated();
        jakarta.ws.rs.WebApplicationException ex =
                backendError(402, "{\"error\":\"Balance too low for MAIL_PURCHASE\"}");
        when(backendClient.createMailbox(eq(TEST_DOMAIN), any(), eq(TEST_TOKEN))).thenThrow(ex);

        MailboxCreateResult result = mailService.createMailbox(TEST_DOMAIN, "info", TEST_PACKAGE_ID, "ANNUAL");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Balance too low"));
        assertTrue(result.getMessage().contains("createPaymentSession"));
    }

    // ===== mailboxes / usage =====

    @Test
    void listMailboxes_success() {
        authenticated();
        MailboxSummary box = new MailboxSummary();
        box.setEmailAddress("info@example.com");
        when(backendClient.getMailboxes(TEST_TOKEN)).thenReturn(List.of(box));

        MailboxListResult result = mailService.listMailboxes();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCount());
    }

    @Test
    void deleteMailbox_mentionsGracePeriod() {
        authenticated();

        MailActionResult result = mailService.deleteMailbox("mb-1");

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("14-day"));
        verify(backendClient).deleteMailbox("mb-1", TEST_TOKEN);
    }

    @Test
    void getUsage_success() {
        authenticated();
        when(backendClient.getUsage(TEST_TOKEN)).thenReturn(Map.of("info@example.com", 123456L));

        MailUsageResult result = mailService.getUsage();

        assertTrue(result.isSuccess());
        assertEquals(123456L, result.getUsage().get("info@example.com"));
    }
}
