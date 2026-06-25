package com.osir.mcp;

import com.osir.mcp.models.*;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.DomainService;
import com.osir.mcp.services.DomainSuggestionService;
import com.osir.mcp.services.McpAuthHelper;
import com.osir.mcp.services.SessionAwareAuthService;
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

class DomainRegistrarMCPServerNewToolsTest {

    @Mock
    DomainService domainService;

    @Mock
    SessionAwareAuthService sessionAuthService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    DomainSuggestionService domainSuggestionService;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    DomainRegistrarMCPServer mcpServer;

    private static final String TEST_DOMAIN = "example.com";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-connection-id");
    }

    // ===== renewDomain =====

    @Test
    void renewDomain_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "renewDomain", "summary");
        when(pendingActionStore.stage(eq("renewDomain"), any(), eq("test-connection-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.renewDomain(TEST_DOMAIN, 3, mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("renewDomain"), any(), eq("test-connection-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any());
    }

    // ===== lockDomain =====

    @Test
    void lockDomain_delegatesToService() {
        DomainActionResult expected = new DomainActionResult(true, "Locked", TEST_DOMAIN, "locked");
        when(domainService.lockDomain(TEST_DOMAIN)).thenReturn(expected);

        DomainActionResult result = mcpServer.lockDomain(TEST_DOMAIN, mockConnection);

        assertSame(expected, result);
        verify(domainService).lockDomain(TEST_DOMAIN);
    }

    @Test
    void lockDomain_handlesException() {
        when(domainService.lockDomain(TEST_DOMAIN)).thenThrow(new RuntimeException("Fail"));

        DomainActionResult result = mcpServer.lockDomain(TEST_DOMAIN, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== unlockDomain =====

    @Test
    void unlockDomain_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "unlockDomain", "summary");
        when(pendingActionStore.stage(eq("unlockDomain"), any(), eq("test-connection-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.unlockDomain(TEST_DOMAIN, mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("unlockDomain"), any(), eq("test-connection-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    // ===== updateDomainAutoRenew =====

    @Test
    void updateDomainAutoRenew_delegatesToService() {
        DomainActionResult expected = new DomainActionResult(true, "Enabled", TEST_DOMAIN, "auto_renew_enabled");
        when(domainService.updateAutoRenew(TEST_DOMAIN, true)).thenReturn(expected);

        DomainActionResult result = mcpServer.updateDomainAutoRenew(TEST_DOMAIN, true, mockConnection);

        assertSame(expected, result);
        verify(domainService).updateAutoRenew(TEST_DOMAIN, true);
    }

    @Test
    void updateDomainAutoRenew_handlesException() {
        when(domainService.updateAutoRenew(TEST_DOMAIN, false)).thenThrow(new RuntimeException("Fail"));

        DomainActionResult result = mcpServer.updateDomainAutoRenew(TEST_DOMAIN, false, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== updateDomainPrivacy =====

    @Test
    void updateDomainPrivacy_delegatesToService() {
        DomainActionResult expected = new DomainActionResult(true, "Enabled", TEST_DOMAIN, "privacy_enabled");
        when(domainService.updatePrivacyProtection(TEST_DOMAIN, true)).thenReturn(expected);

        DomainActionResult result = mcpServer.updateDomainPrivacy(TEST_DOMAIN, true, mockConnection);

        assertSame(expected, result);
        verify(domainService).updatePrivacyProtection(TEST_DOMAIN, true);
    }

    @Test
    void updateDomainPrivacy_handlesException() {
        when(domainService.updatePrivacyProtection(TEST_DOMAIN, false)).thenThrow(new RuntimeException("Fail"));

        DomainActionResult result = mcpServer.updateDomainPrivacy(TEST_DOMAIN, false, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
