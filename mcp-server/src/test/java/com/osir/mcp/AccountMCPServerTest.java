package com.osir.mcp;

import com.osir.mcp.models.account.AccountSummaryResult;
import com.osir.mcp.models.account.UserProfileResult;
import com.osir.mcp.services.AccountService;
import com.osir.mcp.services.McpAuthHelper;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountMCPServerTest {

    @Mock
    AccountService accountService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    AccountMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getMyProfile_delegatesToService() {
        UserProfileResult expected = new UserProfileResult(true, "OK");
        when(accountService.getMyProfile()).thenReturn(expected);
        assertSame(expected, mcpServer.getMyProfile(mockConnection));
    }

    @Test
    void getMyProfile_handlesException() {
        when(accountService.getMyProfile()).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getMyProfile(mockConnection).isSuccess());
    }

    @Test
    void getAccountSummary_delegatesToService() {
        AccountSummaryResult expected = new AccountSummaryResult(true, "OK");
        when(accountService.getAccountSummary()).thenReturn(expected);
        assertSame(expected, mcpServer.getAccountSummary(mockConnection));
    }

    @Test
    void getAccountSummary_handlesException() {
        when(accountService.getAccountSummary()).thenThrow(new RuntimeException("Fail"));
        assertFalse(mcpServer.getAccountSummary(mockConnection).isSuccess());
    }
}
