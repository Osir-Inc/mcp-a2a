package com.osir.mcp.services;

import com.osir.mcp.models.deploy.AgentDtos.AgentActionResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentDeployResult;
import com.osir.mcp.models.deploy.AgentDtos.AgentInstallEnvelope;
import com.osir.mcp.models.deploy.AgentDtos.PasswordEnvelope;
import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppListResult;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.C2Error;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceListResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.models.vps.VpsPackageDetailResult;
import com.osir.mcp.models.vps.VpsPackageSummary;
import com.osir.mcp.services.DeploymentService.AgentCall;
import com.osir.mcp.telemetry.BackendErrorCaptureFilter;
import com.osir.mcp.telemetry.TelemetryEvents;
import io.quarkiverse.mcp.server.McpConnection;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientResponseContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentServiceTest {

    static final String PASSWORD = "Pw-7f3K-q9Zx-ONLY-ONCE";
    static final String API_KEY = "sk-deepseek-NEVER-ECHO-1234";

    DeploymentService deploymentService = mock(DeploymentService.class);
    VpsService vpsService = mock(VpsService.class);
    AuthService authService = mock(AuthService.class);
    MoveToOwnedService moveToOwned = new MoveToOwnedService();
    AgentService service = new AgentService();

    @BeforeEach
    void setUp() {
        moveToOwned.deploymentService = deploymentService;
        moveToOwned.vpsService = vpsService;
        moveToOwned.dnsService = mock(DnsService.class);
        moveToOwned.domainService = mock(DomainService.class);
        moveToOwned.authService = authService;
        moveToOwned.platformSshPubkey = "ssh-ed25519 AAAA test@platform";
        moveToOwned.nameserverSuffix = "osir.com";
        moveToOwned.pollIntervalMs = 1;
        moveToOwned.pollBudgetMs = 50;
        service.deploymentService = deploymentService;
        service.moveToOwnedService = moveToOwned;
        service.vpsService = vpsService;
        service.orderable = "none";
        service.platformSshPubkey = "";
        when(authService.getCurrentToken()).thenReturn("Bearer tok");
        when(authService.parseJwtClaims("tok")).thenReturn(Map.of("sub", "user-1"));
        apps();
        boxes();
    }

    // ---- helpers ----

    private void apps(AppDto... apps) {
        when(deploymentService.listApps()).thenReturn(new AppListResult(true, "ok", List.of(apps)));
    }

    private void boxes(VpsInstanceSummary... boxes) {
        VpsInstanceListResult r = new VpsInstanceListResult(true, "ok");
        r.setInstances(List.of(boxes));
        when(vpsService.listMyInstances()).thenReturn(r);
    }

    private static VpsInstanceSummary box(String id, String hostname, int memoryMb) {
        VpsInstanceSummary s = new VpsInstanceSummary();
        s.setId(id);
        s.setHostname(hostname);
        s.setBuildState("COMPLETE");
        s.setIpAddress("203.0.113.7");
        VpsInstanceSummary.VpsPackageInfo pkg = new VpsInstanceSummary.VpsPackageInfo();
        pkg.setMemoryMb(memoryMb);
        s.setVpsPackage(pkg);
        return s;
    }

    private void owned(VpsInstanceSummary box) {
        VpsInstanceDetailResult r = new VpsInstanceDetailResult(true, "ok");
        r.setInstance(box);
        when(vpsService.getInstanceDetails(box.getId())).thenReturn(r);
    }

    private static AppDto agent(String name) {
        return new AppDto("app_" + name, name, "us", "owned", null, "node", "READY", "https://" + name + ".example.com",
                null, "agent", "hermes");
    }

    private void installAnswers(String password) {
        when(deploymentService.installAgent(anyString(), eq("hermes"), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> new AgentCall<>(new AgentInstallEnvelope(agent(inv.getArgument(0)), "MOVING", "admin",
                        password), 200, null));
    }

    /** Everything any logging backend can emit during {@code run}: stdout, stderr and the JUL root logger. */
    private static String captureLogs(Runnable run) {
        PrintStream out = System.out, err = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        List<String> records = new ArrayList<>();
        Handler jul = new Handler() {
            @Override public void publish(LogRecord r) {
                records.add(r.getMessage() + " " + java.util.Arrays.toString(r.getParameters()));
            }
            @Override public void flush() { }
            @Override public void close() { }
        };
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        root.addHandler(jul);
        System.setOut(new PrintStream(buf, true));
        System.setErr(new PrintStream(buf, true));
        try {
            run.run();
        } finally {
            System.setOut(out);
            System.setErr(err);
            root.removeHandler(jul);
        }
        return buf + String.join("\n", records);
    }

    // ---- the password -----------------------------------------------------------------------------

    @Test
    void install_onOwnedBox_returnsThePasswordOnce_andNoLogTelemetryOrGlitchTipPathSeesIt() throws Exception {
        VpsInstanceSummary vps = box("vf-1", "my-box", 8192);
        owned(vps);
        installAnswers(PASSWORD);
        Object[] result = new Object[1];

        String logs = captureLogs(() -> result[0] = service.deployWithoutSpending("hermes", "vf-1", null, null));

        AgentDeployResult r = (AgentDeployResult) result[0];
        assertTrue(r.success(), r.message());
        assertEquals(PASSWORD, r.password(), "the user must get the password, in the tool result");
        assertEquals("app_hermes-1", r.agentId());
        assertTrue(r.nextStep().contains("only this once"));
        verify(deploymentService).installAgent("hermes-1", "hermes", "vf-1", "203.0.113.7", null, null);

        // The capture must actually see log output, or "no password in it" proves nothing.
        assertTrue(logs.contains("attaching hermes-1"), "log capture saw nothing: " + logs);
        assertFalse(logs.contains(PASSWORD), "the password reached a log");

        // Telemetry: the event built from this very result.
        McpConnection conn = mock(McpConnection.class);
        when(conn.id()).thenReturn("conn-1");
        Map<String, Object> event = TelemetryEvents.event(conn, "osirAgentDeploy", "oauth", "user-1", 12, true, r);
        assertFalse(event.toString().contains(PASSWORD), "the password reached telemetry");
        assertFalse(r.toString().contains(PASSWORD), "toString() would print the password");

        // GlitchTip: the backend-response hook never captures the 202 that carries the password.
        ClientRequestContext req = mock(ClientRequestContext.class);
        ClientResponseContext resp = mock(ClientResponseContext.class);
        when(resp.getStatus()).thenReturn(202);
        new BackendErrorCaptureFilter().filter(req, resp);
        verifyNoInteractions(req);
    }

    @Test
    void retry_thatResumes_neverClaimsToShowAPassword() {
        owned(box("vf-1", "my-box", 8192));
        installAnswers(null);

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-1", null, null);

        assertTrue(r.success());
        assertNull(r.password());
        assertTrue(r.message().contains("Installation resumed"), r.message());
        assertTrue(r.message().contains("osirAgentResetPassword"));
    }

    @Test
    void reinstallOnABoxWhoseAgentIsInstalled_saysSo_andSendsNothing() {
        owned(box("vf-1", "my-box", 8192));
        apps(agent("hermes-1"));
        when(deploymentService.getStatus("hermes-1")).thenReturn(new AppStatusResult(true, "OK", agent("hermes-1"),
                null, null, List.of(), null, "vf-1", "203.0.113.7",
                new com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto("MOVED", "OWNED_AGENT_INSTALLED", null, "t")));

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-1", null, null);

        assertEquals("INSTALLED", r.status());
        assertNull(r.password());
        verify(deploymentService, never()).installAgent(any(), any(), any(), any(), any(), any());
    }

    // ---- review fixes: never a second VPS, never a restart of a running install ---------------------

    private void agentState(String name, String boundBox, String state) {
        when(deploymentService.getStatus(name)).thenReturn(new AppStatusResult(true, "OK", agent(name), null, null,
                List.of(), null, boundBox, "203.0.113.7",
                state == null ? null : new com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto(state, "OWNED_INSTALLING_AGENT",
                        "timed out", "t")));
    }

    @Test
    void retryWithoutInstanceId_afterAFailedInstall_resumesOnTheSameBox_neverOrders() {
        apps(agent("hermes-1"));
        VpsInstanceSummary vps = box("vf-1", "hermes-1-owned.osir.app", 8192);
        boxes(vps);
        owned(vps);
        agentState("hermes-1", "vf-1", "FAILED");
        installAnswers(null);

        Object r = service.deployWithoutSpending("hermes", null, null, null);

        assertNotNull(r, "null would let the caller stage a second VPS order");
        verify(deploymentService).installAgent("hermes-1", "hermes", "vf-1", "203.0.113.7", null, null);
    }

    @Test
    void aSecondConfirmedOrder_rechecksAtExecuteTime_andDoesNotBuyAgain() {
        apps(agent("hermes-1"));
        agentState("hermes-1", "vf-1", "MOVED");
        AgentService.OrderPlan plan = new AgentService.OrderPlan("hermes", new MoveToOwnedService.Prepared(1, "Ubuntu 24.04", List.of(1)));

        AgentDeployResult r = (AgentDeployResult) service.orderAndInstall(plan, "pkg-8g", null, null);

        assertEquals("INSTALLED", r.status());
        verify(vpsService, never()).orderVps(any(), any(), any(), any(), any());
    }

    @Test
    void anInstallStillRunning_isReported_andNotReSent() {
        apps(agent("hermes-1"));
        owned(box("vf-1", "my-box", 8192));
        agentState("hermes-1", "vf-1", "MOVING");

        AgentDeployResult viaBox = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-1", null, null);
        AgentDeployResult viaRetry = (AgentDeployResult) service.deployWithoutSpending("hermes", null, null, null);

        assertEquals("INSTALLING", viaBox.status());
        assertEquals("INSTALLING", viaRetry.status());
        verify(deploymentService, never()).installAgent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anAgentInstalledLongAgo_noMoveRow_isInstalled_everywhere() {
        apps(agent("hermes-1"));
        owned(box("vf-1", "my-box", 8192));
        agentState("hermes-1", "vf-1", null);
        dashboardAnswers(401);

        AgentDeployResult again = (AgentDeployResult) service.deployWithoutSpending("hermes", null, null, null);
        assertEquals("INSTALLED", again.status(), "a re-call must not order or reinstall");
        assertTrue(service.status("hermes-1").installed());
        verify(deploymentService, never()).installAgent(any(), any(), any(), any(), any(), any());
    }

    private void dashboardAnswers(int code) {
        try {
            java.net.http.HttpClient http = mock(java.net.http.HttpClient.class);
            if (code == 0) {
                when(http.send(any(), any())).thenThrow(new java.net.ConnectException("refused"));
            } else {
                @SuppressWarnings("unchecked")
                java.net.http.HttpResponse<Object> resp = mock(java.net.http.HttpResponse.class);
                when(resp.statusCode()).thenReturn(code);
                when(http.send(any(), any())).thenReturn(resp);
            }
            service.http = http;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void platformSaysMoved(String name, String since) {
        when(deploymentService.getStatus(name)).thenReturn(new AppStatusResult(true, "OK", agent(name), null, null,
                List.of(), null, "vf-1", "203.0.113.7",
                new com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto("MOVED", "MOVED_TO_OWNED", null, since)));
    }

    // ---- 2026-11-16 live run: C2 said MOVED, the box served only Caddy's default page ---------------

    @Test
    void platformDone_butNoDnsRecord_isNotInstalled_andSaysWaitingWillNotHelp() {
        platformSaysMoved("hermes-1", java.time.Instant.now().toString());
        dashboardAnswers(0);
        service.resolves = h -> false;

        var r = service.status("hermes-1");

        assertFalse(r.installed());
        assertEquals("MOVED", r.installState(), "C2's own state stays visible");
        assertTrue(r.message().contains("no DNS record"), r.message());
        assertTrue(r.message().contains("Do NOT tell the user to wait"), r.message());
        assertFalse(r.message().contains("check again"), r.message());
    }

    @Test
    void platformDone_dashboardSilentPastTheGrace_isTerminal_notTransient() {
        platformSaysMoved("hermes-1", java.time.Instant.now().minus(java.time.Duration.ofMinutes(9 + 10)).toString());
        dashboardAnswers(0);
        service.resolves = h -> true;

        var r = service.status("hermes-1");

        assertFalse(r.installed());
        assertTrue(r.message().contains("19 minutes ago"), r.message());
        assertTrue(r.message().contains("will not fix itself"), r.message());
    }

    @Test
    void platformDone_dashboardSilentJustNow_isTransient() {
        platformSaysMoved("hermes-1", java.time.Instant.now().toString());
        dashboardAnswers(502);
        service.resolves = h -> true;

        var r = service.status("hermes-1");

        assertFalse(r.installed());
        assertTrue(r.message().contains("check again in a minute"), r.message());
    }

    @Test
    void dashboardProbe_onlyA2xxMeansOpen_and401MeansProtected() throws Exception {
        java.net.http.HttpClient http = mock(java.net.http.HttpClient.class);
        @SuppressWarnings("unchecked")
        java.net.http.HttpResponse<Object> resp = mock(java.net.http.HttpResponse.class);
        when(http.send(any(), any())).thenReturn(resp);
        service.http = http;
        when(resp.statusCode()).thenReturn(502, 401, 200, 302);

        assertNull(service.dashboardProtected("https://a.example.com"), "502 while restarting proves nothing");
        assertEquals(Boolean.TRUE, service.dashboardProtected("https://a.example.com"));
        assertEquals(Boolean.FALSE, service.dashboardProtected("https://a.example.com"));
        assertNull(service.dashboardProtected("https://a.example.com"));
    }

    @Test
    void aFailedInstall_keepsTheInstanceId_soTheRetryCanUseIt() {
        owned(box("vf-9", "my-box", 8192));
        when(deploymentService.installAgent(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new AgentCall<>(null, 409, C2Error.of("BOX_PORTS_IN_USE: nginx holds 80", false)));

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-9", null, null);

        assertFalse(r.success());
        assertEquals("vf-9", r.instanceId());
        assertTrue(r.nextStep().contains("instanceId='vf-9'") && r.nextStep().contains("never with packageId"));
    }

    @Test
    void resetPassword_returnsItOnce_andNeverLogsIt() {
        when(deploymentService.getStatus("app_hermes-1")).thenReturn(statusOf(agent("hermes-1")));
        when(deploymentService.resetAgentPassword("app_hermes-1"))
                .thenReturn(new AgentCall<>(new PasswordEnvelope(PASSWORD), 200, null));
        AgentActionResult[] r = new AgentActionResult[1];

        String logs = captureLogs(() -> r[0] = service.resetPassword("app_hermes-1"));

        assertEquals(PASSWORD, r[0].password());
        assertFalse(logs.contains(PASSWORD));
        assertFalse(r[0].toString().contains(PASSWORD));
    }

    // ---- refusals ---------------------------------------------------------------------------------

    @Test
    void notReleasedYet_503_isRelayedPlainly_andNothingElseIsTried() {
        owned(box("vf-1", "my-box", 8192));
        when(deploymentService.installAgent(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new AgentCall<>(null, 503,
                        C2Error.of("hermes is not available yet — it has not been released on Osir", false)));

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-1", null, null);

        assertFalse(r.success());
        assertTrue(r.message().contains("not available on Osir yet"), r.message());
        assertTrue(r.message().contains("no osirAppDeploy"));
        assertFalse(moveToOwned.hasOrderedInstance("hermes-1"), "tracker must be released");
    }

    @Test
    void ownedBoxUnder4GB_isRefusedBeforeAnyInstall() {
        owned(box("vf-small", "tiny", 2048));

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", "vf-small", null, null);

        assertFalse(r.success());
        assertTrue(r.message().contains("2048 MB"), r.message());
        verify(deploymentService, never()).installAgent(any(), any(), any(), any(), any(), any());
    }

    @Test
    void orderingANewVps_staysClosedUntilTheAgentIsMarkedOrderable() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.planOrder("hermes", "pkg-8g"));
        assertTrue(e.getMessage().contains("not open yet"));
        verify(vpsService, never()).getPackageDetails(anyString());
        verify(vpsService, never()).orderVps(any(), any(), any(), any(), any());
    }

    @Test
    void orderingAPackageUnder4GB_isRefusedBeforeStaging() {
        service.orderable = "hermes";
        VpsPackageSummary small = mock(VpsPackageSummary.class);
        when(small.getMemoryMb()).thenReturn(2048);
        VpsPackageDetailResult pkg = mock(VpsPackageDetailResult.class);
        when(pkg.isSuccess()).thenReturn(true);
        when(pkg.getPackageDetail()).thenReturn(small);
        when(vpsService.getPackageDetails("pkg-2g")).thenReturn(pkg);

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> service.planOrder("hermes", "pkg-2g"));
        assertTrue(e.getMessage().contains("at least 4096 MB"), e.getMessage());
        verify(vpsService, never()).orderVps(any(), any(), any(), any(), any());
    }

    // ---- naming -----------------------------------------------------------------------------------

    @Test
    void names_skipTakenOnes_andAnOrderedBoxWithoutAnAgentIsInstalledOn_notReordered() {
        apps(agent("hermes-1"));
        VpsInstanceSummary orphan = box("vf-2", "hermes-2-owned.osir.app", 8192);
        boxes(box("vf-1", "hermes-1-owned.osir.app", 8192), orphan);
        owned(orphan);
        installAnswers(PASSWORD);

        AgentDeployResult r = (AgentDeployResult) service.deployWithoutSpending("hermes", null, null, null);

        verify(deploymentService).installAgent("hermes-2", "hermes", "vf-2", "203.0.113.7", null, null);
        assertTrue(r.success());
        verify(vpsService, never()).orderVps(any(), any(), any(), any(), any());
        assertEquals("hermes-3", service.names("hermes").free());
    }

    @Test
    void noBoxAndNoPackage_meansNothingToResume() {
        assertNull(service.deployWithoutSpending("hermes", null, null, null), "caller must ask for instanceId/packageId");
    }

    // ---- configure / logs -------------------------------------------------------------------------

    @Test
    void configure_unknownProviderRefused_andTheKeyIsNeverEchoed() {
        AgentActionResult bad = service.configure("app_hermes-1", "mistral", API_KEY);
        assertFalse(bad.success());
        assertFalse(bad.message().contains(API_KEY));

        when(deploymentService.getStatus("app_hermes-1")).thenReturn(statusOf(agent("hermes-1")));
        when(deploymentService.configureAgent("app_hermes-1", "deepseek", API_KEY))
                .thenReturn(new AgentCall<>(new com.osir.mcp.models.deploy.AgentDtos.ProviderEnvelope("MOVING"), 200, null));
        AgentActionResult ok = service.configure("app_hermes-1", "DeepSeek", API_KEY);
        assertTrue(ok.success(), ok.message());
        assertFalse((ok.message() + ok.nextStep()).contains(API_KEY));
    }

    @Test
    void logs_sayNotAvailableYet_withoutCallingTheBackend() {
        when(deploymentService.getStatus("app_hermes-1")).thenReturn(statusOf(agent("hermes-1")));
        AgentActionResult r = service.logs("app_hermes-1");
        assertTrue(r.message().contains("not available"));
        verify(deploymentService, never()).getLogs(any(), any());
    }

    @Test
    void agentTools_refuseAnApp() {
        AppDto app = new AppDto("app_shop", "shop", "us", "instant", null, "node", "READY", null, null, "app", null);
        when(deploymentService.getStatus("shop")).thenReturn(statusOf(app));
        assertTrue(service.requireAgent("shop").contains("is an app, not an agent"));
    }

    private static AppStatusResult statusOf(AppDto app) {
        return new AppStatusResult(true, "OK", app, null, null, List.of(), null, null, null, null);
    }
}
