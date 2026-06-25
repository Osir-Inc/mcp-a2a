package com.osir.mcp;

import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import com.osir.mcp.models.vps.*;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.PendingActionStore;
import com.osir.mcp.services.VpsService;
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

class VpsHostingMCPServerTest {

    @Mock
    VpsService vpsService;

    @Mock
    McpAuthHelper mcpAuthHelper;

    @Mock
    PendingActionStore pendingActionStore;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    VpsHostingMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn-id");
    }

    // ===== listVpsPackages =====

    @Test
    void listVpsPackages_delegatesToService() {
        VpsPackageListResult expected = new VpsPackageListResult(true, "OK");
        when(vpsService.listPackages()).thenReturn(expected);

        VpsPackageListResult result = mcpServer.listVpsPackages(mockConnection);

        assertSame(expected, result);
        verify(vpsService).listPackages();
    }

    @Test
    void listVpsPackages_handlesException() {
        when(vpsService.listPackages()).thenThrow(new RuntimeException("Fail"));

        VpsPackageListResult result = mcpServer.listVpsPackages(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== listVpsLocations =====

    @Test
    void listVpsLocations_delegatesToService() {
        VpsLocationListResult expected = new VpsLocationListResult(true, "OK");
        when(vpsService.listLocations()).thenReturn(expected);

        VpsLocationListResult result = mcpServer.listVpsLocations(mockConnection);

        assertSame(expected, result);
        verify(vpsService).listLocations();
    }

    @Test
    void listVpsLocations_handlesException() {
        when(vpsService.listLocations()).thenThrow(new RuntimeException("Fail"));

        VpsLocationListResult result = mcpServer.listVpsLocations(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== getVpsPackageDetails =====

    @Test
    void getVpsPackageDetails_delegatesToService() {
        VpsPackageDetailResult expected = new VpsPackageDetailResult(true, "OK");
        when(vpsService.getPackageDetails("pkg-1")).thenReturn(expected);

        VpsPackageDetailResult result = mcpServer.getVpsPackageDetails("pkg-1", mockConnection);

        assertSame(expected, result);
        verify(vpsService).getPackageDetails("pkg-1");
    }

    @Test
    void getVpsPackageDetails_handlesException() {
        when(vpsService.getPackageDetails("pkg-1")).thenThrow(new RuntimeException("Fail"));

        VpsPackageDetailResult result = mcpServer.getVpsPackageDetails("pkg-1", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== orderVps =====

    @Test
    void orderVps_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "orderVps", "summary");
        when(pendingActionStore.stage(eq("orderVps"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.orderVps("pkg-1", "myserver.com", "MONTHLY", null, mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("orderVps"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.FINANCIAL), any());
    }

    // ===== listMyVpsInstances =====

    @Test
    void listMyVpsInstances_delegatesToService() {
        VpsInstanceListResult expected = new VpsInstanceListResult(true, "OK");
        when(vpsService.listMyInstances()).thenReturn(expected);

        VpsInstanceListResult result = mcpServer.listMyVpsInstances(mockConnection);

        assertSame(expected, result);
        verify(vpsService).listMyInstances();
    }

    @Test
    void listMyVpsInstances_handlesException() {
        when(vpsService.listMyInstances()).thenThrow(new RuntimeException("Fail"));

        VpsInstanceListResult result = mcpServer.listMyVpsInstances(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== getVpsInstanceDetails =====

    @Test
    void getVpsInstanceDetails_delegatesToService() {
        VpsInstanceDetailResult expected = new VpsInstanceDetailResult(true, "OK");
        when(vpsService.getInstanceDetails("vps-1")).thenReturn(expected);

        VpsInstanceDetailResult result = mcpServer.getVpsInstanceDetails("vps-1", mockConnection);

        assertSame(expected, result);
        verify(vpsService).getInstanceDetails("vps-1");
    }

    @Test
    void getVpsInstanceDetails_handlesException() {
        when(vpsService.getInstanceDetails("vps-1")).thenThrow(new RuntimeException("Fail"));

        VpsInstanceDetailResult result = mcpServer.getVpsInstanceDetails("vps-1", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== deleteVpsInstance =====

    @Test
    void deleteVpsInstance_returnsConfirmationRequired() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "deleteVpsInstance", "summary");
        when(pendingActionStore.stage(eq("deleteVpsInstance"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result = mcpServer.deleteVpsInstance("vps-1", mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("deleteVpsInstance"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    // ===== changeVpsPaymentTerm =====

    @Test
    void changeVpsPaymentTerm_delegatesToService() {
        VpsActionResult expected = new VpsActionResult(true, "Changed");
        when(vpsService.changePaymentTerm("vps-1", "ANNUAL")).thenReturn(expected);

        VpsActionResult result = mcpServer.changeVpsPaymentTerm("vps-1", "ANNUAL", mockConnection);

        assertSame(expected, result);
        verify(vpsService).changePaymentTerm("vps-1", "ANNUAL");
    }

    @Test
    void changeVpsPaymentTerm_handlesException() {
        when(vpsService.changePaymentTerm("vps-1", "ANNUAL")).thenThrow(new RuntimeException("Fail"));

        VpsActionResult result = mcpServer.changeVpsPaymentTerm("vps-1", "ANNUAL", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== loginToVpsPanel =====

    @Test
    void loginToVpsPanel_delegatesToService() {
        VpsPanelLoginResult expected = new VpsPanelLoginResult(true, "OK");
        when(vpsService.loginToPanel("vps-1")).thenReturn(expected);

        VpsPanelLoginResult result = mcpServer.loginToVpsPanel("vps-1", mockConnection);

        assertSame(expected, result);
        verify(vpsService).loginToPanel("vps-1");
    }

    @Test
    void loginToVpsPanel_handlesException() {
        when(vpsService.loginToPanel("vps-1")).thenThrow(new RuntimeException("Fail"));

        VpsPanelLoginResult result = mcpServer.loginToVpsPanel("vps-1", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== countMyVpsInstances =====

    @Test
    void countMyVpsInstances_delegatesToService() {
        VpsCountResult expected = new VpsCountResult(true, "OK", 3);
        when(vpsService.countMyInstances()).thenReturn(expected);

        VpsCountResult result = mcpServer.countMyVpsInstances(mockConnection);

        assertSame(expected, result);
        verify(vpsService).countMyInstances();
    }

    @Test
    void countMyVpsInstances_handlesException() {
        when(vpsService.countMyInstances()).thenThrow(new RuntimeException("Fail"));

        VpsCountResult result = mcpServer.countMyVpsInstances(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
