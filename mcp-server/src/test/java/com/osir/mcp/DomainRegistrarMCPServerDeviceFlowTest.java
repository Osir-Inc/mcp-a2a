package com.osir.mcp;

import com.osir.mcp.models.auth.DeviceLoginResult;
import com.osir.mcp.models.auth.DeviceLoginStatusResult;
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
import static org.mockito.Mockito.*;

class DomainRegistrarMCPServerDeviceFlowTest {

    @Mock
    DomainService domainService;

    @Mock
    SessionAwareAuthService sessionAuthService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    DomainSuggestionService domainSuggestionService;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    DomainRegistrarMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-connection-id");
    }

    // ===== loginWithDevice =====

    @Test
    void loginWithDevice_delegatesToService() {
        DeviceLoginResult expected = new DeviceLoginResult(
                true, "Please visit URL", "dev-code", "ABCD-EFGH",
                "https://auth.osir.com/device", "https://auth.osir.com/device?user_code=ABCD-EFGH",
                600, 5);
        when(sessionAuthService.startDeviceLogin("test-connection-id")).thenReturn(expected);

        DeviceLoginResult result = mcpServer.loginWithDevice(mockConnection);

        assertSame(expected, result);
        verify(sessionAuthService).startDeviceLogin("test-connection-id");
    }

    @Test
    void loginWithDevice_handlesException() {
        when(sessionAuthService.startDeviceLogin("test-connection-id"))
                .thenThrow(new RuntimeException("Keycloak unreachable"));

        DeviceLoginResult result = mcpServer.loginWithDevice(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Keycloak unreachable"));
    }

    // ===== checkDeviceLoginStatus =====

    @Test
    void checkDeviceLoginStatus_delegatesToService() {
        DeviceLoginStatusResult expected = new DeviceLoginStatusResult(
                true, "Waiting for user", "pending");
        when(sessionAuthService.checkDeviceLoginStatus("test-connection-id", "dev-code-123")).thenReturn(expected);

        DeviceLoginStatusResult result = mcpServer.checkDeviceLoginStatus("dev-code-123", mockConnection);

        assertSame(expected, result);
        verify(sessionAuthService).checkDeviceLoginStatus("test-connection-id", "dev-code-123");
    }

    @Test
    void checkDeviceLoginStatus_handlesException() {
        when(sessionAuthService.checkDeviceLoginStatus("test-connection-id", "dev-code-123"))
                .thenThrow(new RuntimeException("Network error"));

        DeviceLoginStatusResult result = mcpServer.checkDeviceLoginStatus("dev-code-123", mockConnection);

        assertFalse(result.isSuccess());
        assertEquals("error", result.getStatus());
        assertTrue(result.getMessage().contains("Network error"));
    }
}
