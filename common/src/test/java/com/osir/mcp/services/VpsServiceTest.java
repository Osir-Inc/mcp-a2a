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

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null, null);

        assertTrue(result.isSuccess());
        assertEquals("vps-new", result.getInstanceId());
        assertEquals("myserver.example.com", result.getHostname());
    }

    @Test
    void orderVps_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void orderVps_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.orderVps(any(), eq(TEST_TOKEN))).thenThrow(new RuntimeException("Insufficient balance"));

        VpsOrderResult result = vpsService.orderVps(TEST_PACKAGE_ID, "myserver.example.com", "MONTHLY", null, null);

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

    // ===== SSH keys =====

    /**
     * Build this BEFORE the outer when(...): stubbing a mock inside another when()'s argument
     * evaluation trips Mockito's UnfinishedStubbingException.
     */
    private jakarta.ws.rs.core.Response stubResponse(int status, String json) {
        jakarta.ws.rs.core.Response response = mock(jakarta.ws.rs.core.Response.class);
        when(response.getStatus()).thenReturn(status);
        if (status < 400) {
            VpsSshKey key = new VpsSshKey();
            key.setId(7);
            key.setName("laptop");
            key.setFingerprint("SHA256:abc");
            when(response.readEntity(VpsSshKey.class)).thenReturn(key);
        } else {
            when(response.readEntity(String.class)).thenReturn(json);
        }
        return response;
    }

    @Test
    void storeSshKey_created_reports201AsNewlyStored() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        jakarta.ws.rs.core.Response stub = stubResponse(201, null);
        when(backendClient.storeSshKey(any(), eq(TEST_TOKEN))).thenReturn(stub);

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "ssh-ed25519 AAAA");

        assertTrue(result.isSuccess());
        assertTrue(result.isCreated());
        assertEquals(7, result.getKey().getId());
    }

    /** The backend answers 200 when the account already had this key material — not an error. */
    @Test
    void storeSshKey_alreadyPresent_reports200AsNotCreated() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        jakarta.ws.rs.core.Response stub = stubResponse(200, null);
        when(backendClient.storeSshKey(any(), eq(TEST_TOKEN))).thenReturn(stub);

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "ssh-ed25519 AAAA");

        assertTrue(result.isSuccess());
        assertFalse(result.isCreated());
        assertEquals(7, result.getKey().getId());
        assertTrue(result.getMessage().contains("already had"));
    }

    /**
     * The trap this pins: the method returns Response, so an error status may arrive as a value rather
     * than an exception. Reading `created = status == 201` first would turn a rejected key into a
     * cheerful "you already had this key stored".
     */
    @Test
    void storeSshKey_malformedKey_reportsFailureNotAlreadyStored() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        jakarta.ws.rs.core.Response stub = stubResponse(400, "{\"error\":\"Not a valid OpenSSH public key.\"}");
        when(backendClient.storeSshKey(any(), eq(TEST_TOKEN))).thenReturn(stub);

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "not-a-key");

        assertFalse(result.isSuccess());
        assertFalse(result.isCreated());
        assertNull(result.getKey());
        assertTrue(result.getMessage().contains("Not a valid OpenSSH public key"),
                "should surface the backend's reason, was: " + result.getMessage());
    }

    /**
     * The real production path: the REST client's default exception mapper throws on 4xx before the
     * Response is ever returned, so the status check inside the try never runs. The backend's reason must
     * still reach the user rather than being flattened to "Bad Request, status code 400".
     */
    @Test
    void storeSshKey_clientThrowsWebApplicationException_surfacesBackendReason() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        jakarta.ws.rs.core.Response stub = stubResponse(400, "{\"error\":\"Not a valid OpenSSH public key.\"}");
        // A real exception, not mock(WebApplicationException.class). VpsService logs the throwable it
        // catches, and rendering a *mocked* Throwable's stack trace never returns - it hangs the test
        // JVM after the assertions have already passed, so the class looks green but the build stalls
        // forever. Mocking a Throwable buys nothing here anyway: the constructor takes the Response.
        when(stub.getStatusInfo()).thenReturn(jakarta.ws.rs.core.Response.Status.BAD_REQUEST);
        jakarta.ws.rs.WebApplicationException ex = new jakarta.ws.rs.WebApplicationException(stub);
        when(backendClient.storeSshKey(any(), eq(TEST_TOKEN))).thenThrow(ex);

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "not-a-key");

        assertFalse(result.isSuccess());
        assertFalse(result.isCreated());
        assertTrue(result.getMessage().contains("Not a valid OpenSSH public key"),
                "should surface the backend's reason, was: " + result.getMessage());
        assertFalse(result.getMessage().contains("status code"));
    }

    /** If the client does throw on 4xx instead of returning it, that must also read as failure. */
    @Test
    void storeSshKey_clientThrows_reportsFailure() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.storeSshKey(any(), eq(TEST_TOKEN)))
                .thenThrow(new RuntimeException("Unauthorized, status code 401"));

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "ssh-ed25519 AAAA");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("401"));
    }

    @Test
    void storeSshKey_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsSshKeyResult result = vpsService.storeSshKey("laptop", "ssh-ed25519 AAAA");

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }

    @Test
    void listSshKeys_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        VpsSshKeyListApiResponse response = new VpsSshKeyListApiResponse();
        VpsSshKey key = new VpsSshKey();
        key.setId(3);
        response.setKeys(List.of(key));
        when(backendClient.getSshKeys(TEST_TOKEN)).thenReturn(response);

        VpsSshKeyListResult result = vpsService.listSshKeys();

        assertTrue(result.isSuccess());
        assertEquals(1, result.getKeys().size());
        assertEquals(3, result.getKeys().get(0).getId());
    }

    @Test
    void deleteSshKey_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);

        VpsActionResult result = vpsService.deleteSshKey(3);

        assertTrue(result.isSuccess());
        verify(backendClient).deleteSshKey(3, TEST_TOKEN);
    }

    @Test
    void deleteSshKey_backendError() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        doThrow(new RuntimeException("Not found")).when(backendClient).deleteSshKey(eq(3), eq(TEST_TOKEN));

        VpsActionResult result = vpsService.deleteSshKey(3);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Not found"));
    }

    // ===== OS templates / build =====

    private static VpsOsTemplateListApiResponse debian12Response() {
        VpsOsTemplateListApiResponse response = new VpsOsTemplateListApiResponse();
        VpsOsTemplate t = new VpsOsTemplate();
        t.setId(46);
        t.setName("Debian");
        t.setVersion("12 (Bookworm)");
        t.setVariant("Minimal");
        response.setTemplates(List.of(t));
        return response;
    }

    @Test
    void listOsTemplates_byInstance_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getOsTemplates(null, TEST_INSTANCE_ID, null, TEST_TOKEN))
                .thenReturn(debian12Response());

        VpsOsTemplateListResult result = vpsService.listOsTemplates(null, TEST_INSTANCE_ID, null);

        assertTrue(result.isSuccess());
        assertEquals(46, result.getTemplates().get(0).getId());
        assertEquals("Debian 12 (Bookworm) Minimal", result.getTemplates().get(0).getDisplayName());
    }

    /** The pre-order lookup: no instance exists yet, so the package is the only key available. */
    @Test
    void listOsTemplates_byPackage_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getOsTemplates(TEST_PACKAGE_ID, null, null, TEST_TOKEN))
                .thenReturn(debian12Response());

        VpsOsTemplateListResult result = vpsService.listOsTemplates(TEST_PACKAGE_ID, null, null);

        assertTrue(result.isSuccess());
        assertEquals(46, result.getTemplates().get(0).getId());
    }

    /** Blank must not reach the backend as `?packageId=`, which reads as present-but-invalid. */
    @Test
    void listOsTemplates_blankKeyIsTreatedAsAbsent() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.getOsTemplates(null, TEST_INSTANCE_ID, null, TEST_TOKEN))
                .thenReturn(debian12Response());

        assertTrue(vpsService.listOsTemplates("  ", TEST_INSTANCE_ID, null).isSuccess());
        verify(backendClient).getOsTemplates(null, TEST_INSTANCE_ID, null, TEST_TOKEN);
    }

    @Test
    void listOsTemplates_neitherKeyIsRejectedWithoutCallingBackend() {
        when(authService.isAuthenticated()).thenReturn(true);

        VpsOsTemplateListResult result = vpsService.listOsTemplates(null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("packageId or instanceId"));
        verifyNoInteractions(backendClient);
    }

    /** Silently preferring one key would answer a different question than the caller asked. */
    @Test
    void listOsTemplates_bothKeysIsRejectedWithoutCallingBackend() {
        when(authService.isAuthenticated()).thenReturn(true);

        VpsOsTemplateListResult result = vpsService.listOsTemplates(TEST_PACKAGE_ID, TEST_INSTANCE_ID, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("not both"));
        verifyNoInteractions(backendClient);
    }

    @Test
    void buildInstance_success() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        VpsBuildResponse response = new VpsBuildResponse();
        response.setInstanceId(TEST_INSTANCE_ID);
        response.setBuildState("QUEUED");
        response.setOsTemplateId(46);
        when(backendClient.buildVpsInstance(eq(TEST_INSTANCE_ID), any(VpsBuildRequest.class), eq(TEST_TOKEN)))
                .thenReturn(response);

        VpsBuildResult result = vpsService.buildInstance(TEST_INSTANCE_ID, 46, null, List.of(3), null);

        assertTrue(result.isSuccess());
        assertEquals("QUEUED", result.getBuildState());
        assertEquals(46, result.getOsTemplateId());
    }

    /**
     * The backend's reason has to survive. "One or more SSH keys do not belong to this account" is
     * actionable; the framework's "Bad Request, status code 400" is not, and the caller cannot guess
     * what was wrong from it.
     */
    @Test
    void buildInstance_badRequest_surfacesBackendReason() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        jakarta.ws.rs.core.Response stub =
                stubResponse(400, "{\"error\":\"One or more SSH keys do not belong to this account\"}");
        when(stub.getStatusInfo()).thenReturn(jakarta.ws.rs.core.Response.Status.BAD_REQUEST);
        // Constructed before the when(...): the constructor reads getStatusInfo() off the stub, and a
        // mock call inside another when()'s argument evaluation trips UnfinishedStubbingException.
        jakarta.ws.rs.WebApplicationException ex = new jakarta.ws.rs.WebApplicationException(stub);
        when(backendClient.buildVpsInstance(any(), any(), any())).thenThrow(ex);

        VpsBuildResult result = vpsService.buildInstance(TEST_INSTANCE_ID, 46, null, List.of(9), null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("do not belong to this account"),
                "should surface the backend's reason, was: " + result.getMessage());
        assertFalse(result.getMessage().contains("status code"));
    }

    /**
     * A 409 means a build is already running — an ordinary outcome, not a crash. It must not read as a
     * queued build, and it must tell the caller to poll rather than retry.
     */
    @Test
    void buildInstance_conflict_explainsBuildAlreadyRunning() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        // The Response is mocked rather than Response.status(409).build(): these are plain unit tests
        // with no Quarkus runtime, so building a real Response finds no JAX-RS RuntimeDelegate. Built
        // outside the when(...) to avoid nested stubbing.
        jakarta.ws.rs.core.Response conflict = mock(jakarta.ws.rs.core.Response.class);
        when(conflict.getStatus()).thenReturn(409);
        when(conflict.getStatusInfo()).thenReturn(jakarta.ws.rs.core.Response.Status.CONFLICT);
        // The exception itself is real, NOT mock(WebApplicationException.class). VpsService logs the
        // throwables it catches, and rendering a mocked Throwable's stack trace never returns - it hangs
        // the test JVM after the assertions have passed. The 409 branch happens to return before it
        // logs, so a mock survives here today purely by luck; it would hang the moment anyone adds a
        // log line to that branch.
        jakarta.ws.rs.WebApplicationException conflictEx = new jakarta.ws.rs.WebApplicationException(conflict);
        when(backendClient.buildVpsInstance(any(), any(), any())).thenThrow(conflictEx);

        VpsBuildResult result = vpsService.buildInstance(TEST_INSTANCE_ID, 46, null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("already in progress"),
                "409 should be explained, not surfaced as a raw status: " + result.getMessage());
        assertFalse(result.getMessage().contains("status code"));
    }

    /** Any other backend error still reads as a plain failure. */
    @Test
    void buildInstance_backendError_reportsFailure() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn(TEST_TOKEN);
        when(backendClient.buildVpsInstance(any(), any(), any()))
                .thenThrow(new RuntimeException("boom"));

        VpsBuildResult result = vpsService.buildInstance(TEST_INSTANCE_ID, 46, null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("boom"));
    }

    @Test
    void buildInstance_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        VpsBuildResult result = vpsService.buildInstance(TEST_INSTANCE_ID, 46, null, null, null);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Authentication required"));
    }
}
