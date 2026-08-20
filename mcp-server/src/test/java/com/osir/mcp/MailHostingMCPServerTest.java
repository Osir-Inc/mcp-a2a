package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.mail.MailPlanListResult;
import com.osir.mcp.models.mail.MailboxCreateResult;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.MailService;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MailHostingMCPServerTest {

    @Mock
    MailService mailService;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    MailHostingMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    @Test
    void listMailPlans_delegatesToService() {
        MailPlanListResult expected = new MailPlanListResult(true, "OK");
        when(mailService.listPlans()).thenReturn(expected);

        MailPlanListResult result = mcpServer.listMailPlans(null, mockConnection);

        assertSame(expected, result);
        verify(mailService).listPlans();
    }

    @Test
    void createMailbox_stagesAsFinancial_andCallableDelegates() throws Exception {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "createMailbox", "summary");
        when(pendingActionStore.stage(eq("createMailbox"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result =
                mcpServer.createMailbox("example.com", "info", "pkg-1", "ANNUAL", null, mockConnection);

        assertSame(staged, result);
        // The mailbox must only be created when the staged action executes, not at staging time.
        verify(mailService, never()).createMailbox(any(), any(), any(), any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callable<Object>> callable = ArgumentCaptor.forClass(Callable.class);
        verify(pendingActionStore).stage(eq("createMailbox"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), callable.capture());
        MailboxCreateResult created = new MailboxCreateResult(true, "OK");
        when(mailService.createMailbox("example.com", "info", "pkg-1", "ANNUAL")).thenReturn(created);
        assertSame(created, callable.getValue().call());
    }

    @Test
    void deleteMailbox_stagesAsDestructive() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "deleteMailbox", "summary");
        when(pendingActionStore.stage(eq("deleteMailbox"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.deleteMailbox("mb-1", null, mockConnection);

        assertSame(staged, result);
        verify(mailService, never()).deleteMailbox(any());
    }
}
