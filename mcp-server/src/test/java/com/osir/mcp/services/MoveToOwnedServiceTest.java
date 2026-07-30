package com.osir.mcp.services;

import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedResult;
import com.osir.mcp.models.dns.DnsRecord;
import com.osir.mcp.models.dns.DnsRecordListResult;
import com.osir.mcp.models.dns.DnsRecordResult;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.models.vps.VpsOrderResult;
import com.osir.mcp.models.vps.VpsOsTemplate;
import com.osir.mcp.models.vps.VpsOsTemplateListResult;
import com.osir.mcp.models.vps.VpsSshKey;
import com.osir.mcp.models.vps.VpsSshKeyResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveToOwnedServiceTest {

    @Mock DeploymentService deploymentService;
    @Mock VpsService vpsService;
    @Mock DnsService dnsService;
    @Mock AuthService authService;

    @InjectMocks
    MoveToOwnedService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.platformSshPubkey = "ssh-ed25519 AAAA test@platform";
        service.pollIntervalMs = 1;
        service.pollBudgetMs = 50;
        identity("user-1");
    }

    /** Stub the resolved caller identity — the money-rule tracker keys on the token's sub. */
    private void identity(String sub) {
        when(authService.getCurrentToken()).thenReturn("Bearer tok-" + sub);
        when(authService.parseJwtClaims("tok-" + sub)).thenReturn(java.util.Map.of("sub", sub));
    }

    // ---- helpers ----

    private void appRunning(String name) {
        AppDto app = new AppDto(name, name, "us", "shared", null, "node", "READY", "https://" + name + ".osir.app", "v1");
        when(deploymentService.getStatus(name)).thenReturn(
                new AppStatusResult(true, "OK", app, null, null, List.of(), null));
    }

    private void ubuntuTemplate(int id) {
        VpsOsTemplate t = new VpsOsTemplate();
        t.setId(id);
        t.setName("Ubuntu Server");
        t.setVersion("24.04");
        VpsOsTemplateListResult r = new VpsOsTemplateListResult(true, "ok");
        r.setTemplates(List.of(t));
        when(vpsService.listOsTemplates(anyString(), isNull(), anyBoolean())).thenReturn(r);
    }

    private void platformKey(int keyId) {
        VpsSshKey k = new VpsSshKey();
        k.setId(keyId);
        VpsSshKeyResult r = new VpsSshKeyResult(true, "stored");
        r.setKey(k);
        when(vpsService.storeSshKey(anyString(), anyString())).thenReturn(r);
    }

    private void instanceState(String instanceId, String buildState, String ip) {
        VpsInstanceSummary s = new VpsInstanceSummary();
        s.setId(instanceId);
        s.setBuildState(buildState);
        s.setIpAddress(ip);
        VpsInstanceDetailResult r = new VpsInstanceDetailResult(true, "ok");
        r.setInstance(s);
        when(vpsService.getInstanceDetails(instanceId)).thenReturn(r);
    }

    private VpsOrderResult orderOk(String instanceId) {
        VpsOrderResult r = new VpsOrderResult(true, "ordered");
        r.setInstanceId(instanceId);
        return r;
    }

    // ---- prepare ----

    @Test
    void prepareFailsWhenAppMissing() {
        when(deploymentService.getStatus("ghost")).thenReturn(AppStatusResult.fail("nope"));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.prepare("ghost", "OSIR-S"));
        assertTrue(e.getMessage().contains("not found"));
    }

    @Test
    void prepareFailsWithoutUbuntuTemplate() {
        appRunning("app1");
        VpsOsTemplate debian = new VpsOsTemplate();
        debian.setId(7);
        debian.setName("Debian");
        debian.setVersion("12");
        VpsOsTemplateListResult r = new VpsOsTemplateListResult(true, "ok");
        r.setTemplates(List.of(debian));
        when(vpsService.listOsTemplates(anyString(), isNull(), anyBoolean())).thenReturn(r);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.prepare("app1", "OSIR-S"));
        assertTrue(e.getMessage().contains("Ubuntu 24.04"));
    }

    @Test
    void prepareResolvesTemplateAndKey() {
        appRunning("app1");
        ubuntuTemplate(42);
        platformKey(20);

        MoveToOwnedService.Prepared prep = service.prepare("app1", "OSIR-S");

        assertEquals(42, prep.osTemplateId());
        assertEquals(20, prep.sshKeyId());
    }

    // ---- money rule ----

    @Test
    void orderOnceThenResumeNeverReorders() {
        appRunning("app1");
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);

        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20);
        MoveToOwnedResult first = service.orderAndMove("app1", "OSIR-S", prep, null);
        assertEquals("BUILDING", first.status());
        assertTrue(service.hasOrderedInstance("app1"));

        // Resume completes the move without a second order.
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned("app1", "vps-9", "1.2.3.4", null)).thenReturn(true);
        MoveToOwnedResult second = service.resume("app1", null);

        assertTrue(second.success());
        assertEquals("MOVING", second.status());
        verify(vpsService, times(1)).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
        assertFalse(service.hasOrderedInstance("app1"));
    }

    @Test
    void buildFailedNeverReordersAndPointsToFreeRebuild() {
        appRunning("app1");
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "FAILED", "1.2.3.4");

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20), null);

        assertFalse(result.success());
        assertEquals("BUILD_FAILED", result.status());
        assertTrue(result.nextStep().contains("buildVpsInstance"));
        // The instance stays tracked so a later call resumes instead of re-ordering.
        assertTrue(service.hasOrderedInstance("app1"));
        verify(vpsService, times(1)).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void shipFailureKeepsInstanceForRetry() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(false);

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20), null);

        assertFalse(result.success());
        assertTrue(service.hasOrderedInstance("app1"));
    }

    // ---- DNS ----

    @Test
    void dnsBindsApexAOnOsirNameservers() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(true);

        DnsRecord apex = new DnsRecord();
        apex.setId("rec-current");
        apex.setName("adb.al");
        apex.setType("A");
        apex.setContent("9.9.9.9");
        apex.setTtl(300);
        DnsRecordListResult zone = new DnsRecordListResult(true, "ok");
        zone.setRecords(List.of(apex));
        when(dnsService.listRecords("adb.al")).thenReturn(zone);
        when(dnsService.updateRecord(eq("adb.al"), eq("rec-current"), eq("adb.al"), eq("A"), eq("1.2.3.4"), eq(300), isNull()))
                .thenReturn(new DnsRecordResult(true, "updated"));

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20), "adb.al");

        assertTrue(result.success());
        assertEquals(Boolean.TRUE, result.dnsBound());
        // The id passed to update came from the fresh list — the stale-id 500 trap.
        verify(dnsService).updateRecord(eq("adb.al"), eq("rec-current"), any(), any(), any(), any(), any());
    }

    @Test
    void externalNameserversReturnManualInstructions() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(true);
        when(dnsService.listRecords("external.com")).thenReturn(new DnsRecordListResult(false, "zone not found"));

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20), "external.com");

        assertTrue(result.success());
        assertEquals(Boolean.FALSE, result.dnsBound());
        assertTrue(result.nextStep().contains("1.2.3.4"));
        assertTrue(result.nextStep().contains("A record"));
    }

    @Test
    void completeWithoutIpWaitsInsteadOfShippingNull() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", null); // built, IP not yet populated

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20), null);

        assertFalse(result.success());
        assertEquals("BUILDING", result.status());
        verify(deploymentService, never()).moveToOwned(anyString(), anyString(), any(), any());
        assertTrue(service.hasOrderedInstance("app1"), "move must stay resumable");
    }

    @Test
    void resumeWithoutOrderFailsCleanly() {
        MoveToOwnedResult result = service.resume("never-ordered", null);
        assertFalse(result.success());
        assertTrue(result.message().contains("No move in progress"));
    }

    // ---- H1: the money rule holds at EXECUTE time, not just stage time ----

    @Test
    void secondExecuteResumesInsteadOfReordering() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);
        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20);

        // A model retry staged two confirmations; the user executed both.
        MoveToOwnedResult first = service.orderAndMove("app1", "OSIR-S", prep, null);
        MoveToOwnedResult second = service.orderAndMove("app1", "OSIR-S", prep, null);

        assertEquals("BUILDING", first.status());
        assertEquals("BUILDING", second.status());
        // Exactly ONE server was bought.
        verify(vpsService, times(1)).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void orderFailureReleasesReservationSoRetryCanOrder() {
        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", 20);
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(new VpsOrderResult(false, "insufficient balance"))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);

        assertThrows(RuntimeException.class, () -> service.orderAndMove("app1", "OSIR-S", prep, null));
        assertFalse(service.hasOrderedInstance("app1"), "failed order must not leave a reservation behind");

        // A fresh attempt (new confirmation) may order again.
        MoveToOwnedResult retry = service.orderAndMove("app1", "OSIR-S", prep, null);
        assertEquals("BUILDING", retry.status());
        verify(vpsService, times(2)).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }

    // ---- H3: identity keying ----

    @Test
    void trackerIsolatesUsers() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);
        service.orderAndMove("app1", "OSIR-S", new MoveToOwnedService.Prepared(42, "u", 20), null);
        assertTrue(service.hasOrderedInstance("app1"));

        identity("user-2");
        assertFalse(service.hasOrderedInstance("app1"), "another user's move must be invisible");
    }

    @Test
    void unresolvableIdentityFailsClosed() {
        when(authService.getCurrentToken()).thenReturn(null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.hasOrderedInstance("app1"));
        assertTrue(e.getMessage().contains("identity"));
        verify(vpsService, never()).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }
}
