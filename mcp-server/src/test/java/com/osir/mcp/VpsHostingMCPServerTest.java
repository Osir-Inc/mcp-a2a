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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.Callable;

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

        ConfirmationRequiredResult result = mcpServer.orderVps("pkg-1", "myserver.com", "MONTHLY", null, null, mockConnection);

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

    // ===== listVpsOsTemplates =====

    @Test
    void listVpsOsTemplates_byInstance_delegatesToService() {
        VpsOsTemplateListResult expected = new VpsOsTemplateListResult(true, "ok");
        when(vpsService.listOsTemplates(null, "vps-1", null)).thenReturn(expected);

        VpsOsTemplateListResult result = mcpServer.listVpsOsTemplates(null, "vps-1", null, mockConnection);

        assertSame(expected, result);
        verify(vpsService).listOsTemplates(null, "vps-1", null);
    }

    /** The pre-order path: packageId must reach the service in the package slot, not the instance one. */
    @Test
    void listVpsOsTemplates_byPackage_delegatesToService() {
        VpsOsTemplateListResult expected = new VpsOsTemplateListResult(true, "ok");
        when(vpsService.listOsTemplates("pkg-1", null, false)).thenReturn(expected);

        VpsOsTemplateListResult result = mcpServer.listVpsOsTemplates("pkg-1", null, false, mockConnection);

        assertSame(expected, result);
        verify(vpsService).listOsTemplates("pkg-1", null, false);
    }

    @Test
    void listVpsOsTemplates_handlesException() {
        when(vpsService.listOsTemplates(any(), any(), any())).thenThrow(new RuntimeException("Fail"));

        VpsOsTemplateListResult result = mcpServer.listVpsOsTemplates(null, "vps-1", true, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    // ===== buildVpsInstance =====

    @Test
    void buildVpsInstance_stagesAsDestructive() {
        ConfirmationRequiredResult staged = new ConfirmationRequiredResult("test-id", "buildVpsInstance", "summary");
        when(pendingActionStore.stage(eq("buildVpsInstance"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any())).thenReturn(staged);

        ConfirmationRequiredResult result =
                mcpServer.buildVpsInstance("vps-1", 46, null, null, null, mockConnection);

        assertSame(staged, result);
        verify(pendingActionStore).stage(eq("buildVpsInstance"), any(), eq("test-conn-id"),
                eq(DestructiveOpRateLimiter.Bucket.DESTRUCTIVE), any());
    }

    /**
     * The staged summary is the only thing a user reads before approving a disk wipe, so it has to say
     * so in as many words.
     */
    @Test
    void buildVpsInstance_summarySpellsOutDataLoss() {
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);

        mcpServer.buildVpsInstance("vps-1", 46, null, null, null, mockConnection);

        verify(pendingActionStore).stage(eq("buildVpsInstance"), summary.capture(), any(), any(), any());
        assertTrue(summary.getValue().contains("ERASES ALL DATA"),
                "summary must warn about data loss, was: " + summary.getValue());
        assertTrue(summary.getValue().contains("vps-1"));
    }

    /**
     * The other staged tests match the closure with any(), so they never run it — a wrong service call
     * inside the lambda would pass. For a disk wipe, actually execute it.
     */
    @Test
    void buildVpsInstance_stagedClosureCallsBuildWithSuppliedArgs() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callable<Object>> action = ArgumentCaptor.forClass(Callable.class);
        VpsBuildResult expected = new VpsBuildResult(true, "queued");
        when(vpsService.buildInstance("vps-1", 46, "host.example.com", List.of(3), 512.0)).thenReturn(expected);

        mcpServer.buildVpsInstance("vps-1", 46, List.of(3), "host.example.com", 512.0, mockConnection);
        verify(pendingActionStore).stage(eq("buildVpsInstance"), any(), any(), any(), action.capture());

        // Nothing should have happened until the action is confirmed.
        verify(vpsService, never()).buildInstance(any(), any(), any(), any(), any());

        Object result = action.getValue().call();

        assertSame(expected, result);
        verify(vpsService).buildInstance("vps-1", 46, "host.example.com", List.of(3), 512.0);
    }

    // ===== orderVps OS wiring =====

    @Test
    void orderVps_stagedClosurePassesOsAndKeys() throws Exception {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Callable<Object>> action = ArgumentCaptor.forClass(Callable.class);
        VpsOrderResult expected = new VpsOrderResult(true, "ordered");
        when(vpsService.orderVps("pkg-1", "myserver.com", "MONTHLY", 46, List.of(3))).thenReturn(expected);

        mcpServer.orderVps("pkg-1", "myserver.com", "MONTHLY", 46, List.of(3), mockConnection);
        verify(pendingActionStore).stage(eq("orderVps"), any(), any(), any(), action.capture());

        assertSame(expected, action.getValue().call());
        verify(vpsService).orderVps("pkg-1", "myserver.com", "MONTHLY", 46, List.of(3));
    }

    /** Ordering without an OS yields a server with nothing installed — the summary must not hide that. */
    @Test
    void orderVps_summaryFlagsMissingOs() {
        ArgumentCaptor<String> summary = ArgumentCaptor.forClass(String.class);

        mcpServer.orderVps("pkg-1", "myserver.com", "MONTHLY", null, null, mockConnection);

        verify(pendingActionStore).stage(eq("orderVps"), summary.capture(), any(), any(), any());
        assertTrue(summary.getValue().contains("NO operating system"),
                "summary must say the server gets no OS, was: " + summary.getValue());
    }

    // ===== SSH keys =====

    @Test
    void addSshKey_delegatesToService() {
        VpsSshKeyResult expected = new VpsSshKeyResult(true, "SSH key stored.");
        when(vpsService.storeSshKey("laptop", "ssh-ed25519 AAAA")).thenReturn(expected);

        VpsSshKeyResult result = mcpServer.addSshKey("laptop", "ssh-ed25519 AAAA", mockConnection);

        assertSame(expected, result);
        verify(vpsService).storeSshKey("laptop", "ssh-ed25519 AAAA");
    }

    @Test
    void addSshKey_handlesException() {
        when(vpsService.storeSshKey(any(), any())).thenThrow(new RuntimeException("Fail"));

        VpsSshKeyResult result = mcpServer.addSshKey("laptop", "ssh-ed25519 AAAA", mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    @Test
    void listMySshKeys_delegatesToService() {
        VpsSshKeyListResult expected = new VpsSshKeyListResult(true, "ok");
        when(vpsService.listSshKeys()).thenReturn(expected);

        VpsSshKeyListResult result = mcpServer.listMySshKeys(mockConnection);

        assertSame(expected, result);
        verify(vpsService).listSshKeys();
    }

    @Test
    void listMySshKeys_handlesException() {
        when(vpsService.listSshKeys()).thenThrow(new RuntimeException("Fail"));

        VpsSshKeyListResult result = mcpServer.listMySshKeys(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }

    @Test
    void deleteSshKey_delegatesToService() {
        VpsActionResult expected = new VpsActionResult(true, "SSH key 3 deleted.");
        when(vpsService.deleteSshKey(3)).thenReturn(expected);

        VpsActionResult result = mcpServer.deleteSshKey(3, mockConnection);

        assertSame(expected, result);
        verify(vpsService).deleteSshKey(3);
    }

    @Test
    void deleteSshKey_handlesException() {
        when(vpsService.deleteSshKey(anyInt())).thenThrow(new RuntimeException("Fail"));

        VpsActionResult result = mcpServer.deleteSshKey(3, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Fail"));
    }
}
