package com.osir.mcp.services;

import com.osir.mcp.clients.VpsBackendClient;
import com.osir.mcp.models.vps.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VpsServiceTest {

    @Mock
    VpsBackendClient backendClient;

    @Mock
    AuthService authService;

    @InjectMocks
    VpsService vpsService;

    private static final String TEST_TOKEN = "Bearer test-token";
    private static final String TEST_INSTANCE_ID = "vps-123";
    private static final String TEST_PACKAGE_ID = "pkg-1";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== listPackages =====

    @Test
    void listPackages_success() {
        VpsPackageSummary pkg = new VpsPackageSummary();
        pkg.setId("pkg-1");
        pkg.setName("VPS Basic");
        pkg.setCpuCores(2);
        pkg.setMemoryMb(2048);
        VpsPackageListApiResponse apiResponse = new VpsPackageListApiResponse();
        apiResponse.setPackages(List.of(pkg));
        when(backendClient.getVpsPackages()).thenReturn(apiResponse);

        VpsPackageListResult result = vpsService.listPackages();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getPackages().size());
        assertEquals("pkg-1", result.getPackages().get(0).getId());
    }

    @Test
    void listPackages_backendError() {
        when(backendClient.getVpsPackages()).thenThrow(new RuntimeException("Connection refused"));

        VpsPackageListResult result = vpsService.listPackages();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
    }

    // ===== listLocations =====

    @Test
    void listLocations_success() {
        VpsLocation loc = new VpsLocation();
        loc.setId("loc-1");
        loc.setCity("Nuremberg");
        loc.setCountryCode("DE");
        var locResponse = new com.osir.mcp.models.vps.VpsLocationListApiResponse();
        locResponse.setLocations(List.of(loc));
        when(backendClient.getVpsLocations()).thenReturn(locResponse);

        VpsLocationListResult result = vpsService.listLocations();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getLocations().size());
        assertEquals("Nuremberg", result.getLocations().get(0).getCity());
    }

    @Test
    void listLocations_backendError() {
        when(backendClient.getVpsLocations()).thenThrow(new RuntimeException("Timeout"));

        VpsLocationListResult result = vpsService.listLocations();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
    }

    // ===== getPackageDetails =====

    @Test
    void getPackageDetails_success() {
        VpsPackageSummary pkg = new VpsPackageSummary();
        pkg.setId(TEST_PACKAGE_ID);
        pkg.setName("VPS Pro");
        when(backendClient.getVpsPackageDetails(TEST_PACKAGE_ID)).thenReturn(pkg);

        VpsPackageDetailResult result = vpsService.getPackageDetails(TEST_PACKAGE_ID);

        assertTrue(result.isSuccess());
        assertNotNull(result.getPackageDetail());
        assertEquals("VPS Pro", result.getPackageDetail().getName());
    }

    @Test
    void getPackageDetails_notFound() {
        when(backendClient.getVpsPackageDetails("nonexistent")).thenThrow(new RuntimeException("Not found"));

        VpsPackageDetailResult result = vpsService.getPackageDetails("nonexistent");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Not found"));
    }

    // ===== orderVps =====

    @Test
    void orderVps_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsOrderResponse response = new VpsOrderResponse();
        VpsInstanceSummary instance = new VpsInstanceSummary();
        instance.setId("vps-new");
        instance.setHostname("myserver.example.com");
        instance.setStatus("PENDING");
        response.setInstance(instance);
        when(backendClient.orderVps(any(VpsOrderRequest.class), eq(TEST_TOKEN))).thenReturn(response);

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null);

        assertTrue(result.isSuccess());
        assertEquals("vps-new", result.getInstanceId());
        assertEquals("myserver.example.com", result.getHostname());
    }

    @Test
    void orderVps_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void orderVps_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.orderVps(any(), eq(TEST_TOKEN))).thenThrow(new RuntimeException("Insufficient balance"));

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Insufficient balance"));
    }

    // ===== listMyInstances =====

    @Test
    void listMyInstances_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsInstanceSummary instance = new VpsInstanceSummary();
        instance.setId(TEST_INSTANCE_ID);
        instance.setHostname("myserver.example.com");
        instance.setStatus("ACTIVE");
        when(backendClient.getVpsInstances(TEST_TOKEN)).thenReturn(List.of(instance));

        VpsInstanceListResult result = vpsService.listMyInstances();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getInstances().size());
        assertEquals(1, result.getTotalCount());
    }

    @Test
    void listMyInstances_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsInstanceListResult result = vpsService.listMyInstances();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== getInstanceDetails =====

    @Test
    void getInstanceDetails_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsInstanceSummary instance = new VpsInstanceSummary();
        instance.setId(TEST_INSTANCE_ID);
        instance.setHostname("myserver.example.com");
        when(backendClient.getVpsInstanceDetails(TEST_INSTANCE_ID, TEST_TOKEN)).thenReturn(instance);

        VpsInstanceDetailResult result = vpsService.getInstanceDetails(TEST_INSTANCE_ID);

        assertTrue(result.isSuccess());
        assertNotNull(result.getInstance());
        assertEquals(TEST_INSTANCE_ID, result.getInstance().getId());
    }

    @Test
    void getInstanceDetails_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsInstanceDetailResult result = vpsService.getInstanceDetails(TEST_INSTANCE_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== deleteInstance =====

    @Test
    void deleteInstance_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsActionResponse response = new VpsActionResponse();
        response.setMessage("Instance deletion initiated");
        response.setInstanceId(TEST_INSTANCE_ID);
        response.setStatus("DELETING");
        when(backendClient.deleteVpsInstance(TEST_INSTANCE_ID, TEST_TOKEN)).thenReturn(response);

        VpsActionResult result = vpsService.deleteInstance(TEST_INSTANCE_ID);

        assertTrue(result.isSuccess());
        assertEquals(TEST_INSTANCE_ID, result.getInstanceId());
        assertEquals("DELETING", result.getStatus());
    }

    @Test
    void deleteInstance_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsActionResult result = vpsService.deleteInstance(TEST_INSTANCE_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== changePaymentTerm =====

    @Test
    void changePaymentTerm_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsActionResponse response = new VpsActionResponse();
        response.setMessage("Payment term changed");
        response.setInstanceId(TEST_INSTANCE_ID);
        when(backendClient.changePaymentTerm(eq(TEST_INSTANCE_ID), any(VpsPaymentTermRequest.class), eq(TEST_TOKEN)))
                .thenReturn(response);

        VpsActionResult result = vpsService.changePaymentTerm(TEST_INSTANCE_ID, "ANNUAL");

        assertTrue(result.isSuccess());
        assertEquals(TEST_INSTANCE_ID, result.getInstanceId());
    }

    @Test
    void changePaymentTerm_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsActionResult result = vpsService.changePaymentTerm(TEST_INSTANCE_ID, "ANNUAL");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== loginToPanel =====

    @Test
    void loginToPanel_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsPanelLoginResponse response = new VpsPanelLoginResponse();
        response.setMessage("Login URL generated");
        response.setLoginUrl("https://panel.example.com/login?token=abc123");
        when(backendClient.loginToVpsPanel(TEST_INSTANCE_ID, TEST_TOKEN)).thenReturn(response);

        VpsPanelLoginResult result = vpsService.loginToPanel(TEST_INSTANCE_ID);

        assertTrue(result.isSuccess());
        assertNotNull(result.getLoginUrl());
        assertTrue(result.getLoginUrl().contains("panel.example.com"));
    }

    @Test
    void loginToPanel_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsPanelLoginResult result = vpsService.loginToPanel(TEST_INSTANCE_ID);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    // ===== countMyInstances =====

    @Test
    void countMyInstances_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsCountResult countResponse = new VpsCountResult(true, "Count retrieved", 5);
        when(backendClient.getVpsInstanceCount(TEST_TOKEN)).thenReturn(countResponse);

        VpsCountResult result = vpsService.countMyInstances();

        assertTrue(result.isSuccess());
        assertEquals(5, result.getCount());
    }

    @Test
    void countMyInstances_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsCountResult result = vpsService.countMyInstances();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }
}
