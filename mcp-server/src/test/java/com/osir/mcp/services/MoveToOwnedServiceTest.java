package com.osir.mcp.services;

import com.osir.mcp.models.deploy.DeployDtos.AppDto;
import com.osir.mcp.models.deploy.DeployDtos.AppStatusResult;
import com.osir.mcp.models.deploy.DeployDtos.OwnedMoveDto;
import com.osir.mcp.models.deploy.MoveToOwnedDtos.MoveToOwnedResult;
import com.osir.mcp.models.DomainInfoResult;
import com.osir.mcp.models.dns.DnsActionResult;
import com.osir.mcp.models.dns.DnsRecord;
import com.osir.mcp.models.dns.DnsRecordListResult;
import com.osir.mcp.models.dns.DnsRecordResult;
import com.osir.mcp.models.vps.VpsInstanceDetailResult;
import com.osir.mcp.models.vps.VpsInstanceListResult;
import com.osir.mcp.models.vps.VpsInstanceSummary;
import com.osir.mcp.models.vps.VpsOrderResult;
import com.osir.mcp.models.vps.VpsOsTemplate;
import com.osir.mcp.models.vps.VpsOsTemplateListResult;
import com.osir.mcp.models.vps.VpsSshKey;
import com.osir.mcp.models.vps.VpsSshKeyListResult;
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
    @Mock DomainService domainService;
    @Mock AuthService authService;

    @InjectMocks
    MoveToOwnedService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service.platformSshPubkey = "ssh-ed25519 AAAA test@platform";
        service.nameserverSuffix = "osir.com";
        service.pollIntervalMs = 1;
        service.pollBudgetMs = 50;
        identity("user-1");
        // Default: the app exists, owns no box and has no move running, so the durable checks stay
        // quiet unless a test says otherwise.
        lenient().when(deploymentService.getStatus(anyString()))
                .thenAnswer(inv -> statusOf(inv.getArgument(0), null, null));
    }

    /** Stub the resolved caller identity — the money-rule tracker keys on the token's sub. */
    private void identity(String sub) {
        when(authService.getCurrentToken()).thenReturn("Bearer tok-" + sub);
        when(authService.parseJwtClaims("tok-" + sub)).thenReturn(java.util.Map.of("sub", sub));
    }

    // ---- helpers ----

    private AppStatusResult statusOf(String name, String ownedInstanceId, OwnedMoveDto move) {
        AppDto app = new AppDto(name, name, "us", "shared", null, "node", "READY", "https://" + name + ".osir.app", "v1");
        return new AppStatusResult(true, "OK", app, null, null, List.of(), null, ownedInstanceId, null, move);
    }

    private void appRunning(String name) {
        when(deploymentService.getStatus(name)).thenReturn(statusOf(name, null, null));
    }

    /** The app already has a box recorded on C2 — the durable "don't order again" signal. */
    private void appBoundToBox(String name, String instanceId, String ip) {
        when(deploymentService.getStatus(name)).thenReturn(statusOf(name, instanceId, null));
    }

    /** Which nameservers a domain is delegated to — only ours make creating a zone here useful. */
    private void delegation(String domain, String... nameservers) {
        DomainInfoResult info = new DomainInfoResult(domain, true, "ok");
        info.setNameservers(List.of(nameservers));
        when(domainService.getDomainInfo(domain)).thenReturn(info);
    }

    /** C2's audit-derived view of a move: MOVING must suppress a re-dispatch, FAILED must not. */
    private void moveState(String name, String instanceId, String state, String stage) {
        when(deploymentService.getStatus(name)).thenReturn(statusOf(name, instanceId,
                new OwnedMoveDto(state, stage, "scp-to failed (exit 255)", "2026-09-04T09:12:00Z")));
    }

    private void myInstances(VpsInstanceSummary... instances) {
        VpsInstanceListResult r = new VpsInstanceListResult(true, "ok");
        r.setInstances(List.of(instances));
        when(vpsService.listMyInstances()).thenReturn(r);
    }

    private VpsInstanceSummary instance(String id, String hostname) {
        VpsInstanceSummary s = new VpsInstanceSummary();
        s.setId(id);
        s.setHostname(hostname);
        return s;
    }

    private void accountKeys(int... ids) {
        VpsSshKeyListResult r = new VpsSshKeyListResult(true, "ok");
        List<VpsSshKey> keys = new java.util.ArrayList<>();
        for (int id : ids) {
            VpsSshKey k = new VpsSshKey();
            k.setId(id);
            keys.add(k);
        }
        r.setKeys(keys);
        when(vpsService.listSshKeys()).thenReturn(r);
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
    void prepareResolvesTemplateAndInjectsPlatformPlusAccountKeys() {
        appRunning("app1");
        ubuntuTemplate(42);
        platformKey(20);
        accountKeys(7, 20);   // 20 is the platform key already in the list — must not double up

        MoveToOwnedService.Prepared prep = service.prepare("app1", "OSIR-S");

        assertEquals(42, prep.osTemplateId());
        // The customer's own key goes on the box too, or a failed move locks them out of a paid server.
        assertEquals(List.of(20, 7), prep.sshKeyIds());
    }

    @Test
    void prepareStillWorksWhenTheKeyListCannotBeRead() {
        appRunning("app1");
        ubuntuTemplate(42);
        platformKey(20);
        when(vpsService.listSshKeys()).thenReturn(new VpsSshKeyListResult(false, "backend down"));

        assertEquals(List.of(20), service.prepare("app1", "OSIR-S").sshKeyIds());
    }

    // ---- money rule ----

    @Test
    void orderOnceThenResumeNeverReorders() {
        appRunning("app1");
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);

        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20));
        MoveToOwnedResult first = service.orderAndMove("app1", "OSIR-S", prep, null);
        assertEquals("BUILDING", first.status());
        assertTrue(service.hasOrderedInstance("app1"));

        // Resume completes the move without a second order.
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned("app1", "vps-9", "1.2.3.4", null)).thenReturn(null);
        MoveToOwnedResult second = service.resume("app1", null);

        assertTrue(second.success());
        assertEquals("MOVING", second.status());
        // No domain asked for: that is FALSE with a reason, not an unexplained null.
        assertEquals(Boolean.FALSE, second.dnsBound());
        assertTrue(second.nextStep().contains("No domain was given"));
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
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20)), null);

        assertFalse(result.success());
        assertEquals("BUILD_FAILED", result.status());
        assertTrue(result.nextStep().contains("buildVpsInstance"));
        // The instance stays tracked so a later call resumes instead of re-ordering.
        assertTrue(service.hasOrderedInstance("app1"));
        verify(vpsService, times(1)).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void shipFailureKeepsInstanceForRetryAndReportsTheCause() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any()))
                .thenReturn("that box is already bound to another app");

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20)), null);

        assertFalse(result.success());
        assertTrue(service.hasOrderedInstance("app1"));
        // The customer gets C2's actual reason — swallowing it is what made three retries silent.
        assertTrue(result.message().contains("already bound to another app"), result.message());
    }

    @Test
    void thirdIdenticalRefusalStopsTheRetryLoop() {
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any()))
                .thenReturn("this app has no built running version to move yet");

        assertTrue(service.attach("app1", "vps-own", null).nextStep().contains("retry the ship step"));
        assertTrue(service.attach("app1", "vps-own", null).nextStep().contains("retry the ship step"));
        MoveToOwnedResult third = service.attach("app1", "vps-own", null);

        assertTrue(third.nextStep().startsWith("STOP retrying"), third.nextStep());
        assertTrue(third.nextStep().contains("support"));
    }

    @Test
    void aMoveAlreadyRunningIsPolled_notEscalatedToSupport() {
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any()))
                .thenReturn("a move to a different box is already in progress for this app");

        for (int i = 0; i < 3; i++) {
            MoveToOwnedResult r = service.attach("app1", "vps-own", null);
            assertEquals("MOVING", r.status());
            assertFalse(r.nextStep().startsWith("STOP retrying"), r.nextStep());
            assertTrue(r.nextStep().contains("WITHOUT instanceId"), r.nextStep());
        }
    }

    // ---- C2's ownedMove: suppress a re-dispatch only while the move is RUNNING ----

    @Test
    void movingIsReportedInsteadOfReDispatched() {
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        moveState("app1", "vps-own", "MOVING", "OWNED_SHIPPING_IMAGE");

        MoveToOwnedResult result = service.attach("app1", "vps-own", null);

        assertTrue(result.success());
        assertEquals("MOVING", result.status());
        assertTrue(result.message().contains("OWNED_SHIPPING_IMAGE"), result.message());
        verify(deploymentService, never()).moveToOwned(any(), any(), any(), any());
        assertTrue(result.nextStep().contains("two minutes"), result.nextStep());
    }

    @Test
    void aFailedMoveIsStillReDispatched() {
        // The live recovery on 2026-09-04: first ship failed, the re-POST finished it.
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        moveState("app1", "vps-own", "FAILED", "MOVE_TO_OWNED_FAILED");
        when(deploymentService.moveToOwned("app1", "vps-own", "1.2.3.4", null)).thenReturn(null);

        MoveToOwnedResult result = service.attach("app1", "vps-own", null);

        assertTrue(result.success());
        verify(deploymentService).moveToOwned("app1", "vps-own", "1.2.3.4", null);
    }

    @Test
    void anUnreadableStatusDoesNotBlockTheMove() {
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        when(deploymentService.getStatus("app1")).thenReturn(AppStatusResult.fail("backend down"));
        when(deploymentService.moveToOwned("app1", "vps-own", "1.2.3.4", null)).thenReturn(null);

        assertTrue(service.attach("app1", "vps-own", null).success());
        verify(deploymentService).moveToOwned("app1", "vps-own", "1.2.3.4", null);
    }

    // ---- DNS ----

    @Test
    void dnsBindsApexAOnOsirNameservers() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(null);

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
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20)), "adb.al");

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
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(null);
        when(dnsService.listRecords("external.com")).thenReturn(new DnsRecordListResult(false, "zone not found"));
        delegation("external.com", "ns1.cloudflare.com", "ns2.cloudflare.com");

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20)), "external.com");

        assertTrue(result.success());
        assertEquals(Boolean.FALSE, result.dnsBound());
        assertTrue(result.nextStep().contains("1.2.3.4"));
        assertTrue(result.nextStep().contains("A record"));
        // Creating a zone here would write a record no resolver reads, then claim DNS was bound.
        verify(dnsService, never()).initializeZone(anyString());
    }

    @Test
    void completeWithoutIpWaitsInsteadOfShippingNull() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "COMPLETE", null); // built, IP not yet populated

        MoveToOwnedResult result = service.orderAndMove("app1", "OSIR-S",
                new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20)), null);

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
        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20));

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
        MoveToOwnedService.Prepared prep = new MoveToOwnedService.Prepared(42, "Ubuntu Server 24.04", List.of(20));
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
        service.orderAndMove("app1", "OSIR-S", new MoveToOwnedService.Prepared(42, "u", List.of(20)), null);
        assertTrue(service.hasOrderedInstance("app1"));

        identity("user-2");
        assertFalse(service.hasOrderedInstance("app1"), "another user's move must be invisible");
    }

    // ---- C1: a box the customer ALREADY owns is attached, never re-bought ----

    @Test
    void attachShipsToAnOwnedBoxWithoutOrdering() {
        instanceState("vps-own", "COMPLETE", "173.208.224.36");
        when(deploymentService.moveToOwned("app1", "vps-own", "173.208.224.36", null)).thenReturn(null);

        MoveToOwnedResult result = service.attach("app1", "vps-own", null);

        assertTrue(result.success());
        assertEquals("MOVING", result.status());
        assertEquals("vps-own", result.instanceId());
        verify(vpsService, never()).orderVps(anyString(), anyString(), anyString(), anyInt(), anyList());
    }

    @Test
    void attachRefusesAnInstanceThatIsNotOnTheAccount() {
        // Ownership proof: details are read under the CUSTOMER's session, so a foreign box cannot be read.
        when(vpsService.getInstanceDetails("someone-elses"))
                .thenReturn(new VpsInstanceDetailResult(false, "not found"));

        MoveToOwnedResult result = service.attach("app1", "someone-elses", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("listMyVpsInstances"));
        verify(deploymentService, never()).moveToOwned(any(), any(), any(), any());
    }

    @Test
    void attachWillNotHijackAMoveAlreadyRunningOnAnotherBox() {
        when(vpsService.orderVps(anyString(), anyString(), anyString(), anyInt(), anyList()))
                .thenReturn(orderOk("vps-9"));
        instanceState("vps-9", "BUILDING", null);
        service.orderAndMove("app1", "OSIR-S", new MoveToOwnedService.Prepared(42, "u", List.of(20)), null);

        instanceState("vps-other", "COMPLETE", "5.6.7.8");   // a real box of theirs, just not this move's

        MoveToOwnedResult result = service.attach("app1", "vps-other", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("vps-9"));
    }

    @Test
    void findExistingBoxPrefersC2Binding() {
        appBoundToBox("app1", "vps-bound", "1.2.3.4");
        instanceState("vps-bound", "COMPLETE", "1.2.3.4");

        assertEquals("vps-bound", service.findExistingBox("app1"));
        verify(vpsService, never()).listMyInstances();   // C2's binding is authoritative
    }

    @Test
    void aBindingToAVanishedBoxIsIgnoredSoAReplacementCanStillBeOrdered() {
        // The customer cancelled the VPS; C2 still records the binding. Returning it would send every
        // call into attach ("could not be read") and leave no path to a replacement server.
        appBoundToBox("app1", "vps-gone", "1.2.3.4");
        when(vpsService.getInstanceDetails("vps-gone"))
                .thenReturn(new VpsInstanceDetailResult(false, "not found"));
        myInstances(instance("vps-7", "unrelated.example"));

        assertNull(service.findExistingBox("app1"));
    }

    @Test
    void unreadableStatusRefusesToDecideThereIsNoBox() {
        // FAIL CLOSED: "could not check" must not be spent as "no box" — that stages a second order.
        when(deploymentService.getStatus("app1")).thenReturn(AppStatusResult.fail("backend down"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service.findExistingBox("app1"));
        assertTrue(e.getMessage().contains("Nothing was ordered"), e.getMessage());
    }

    @Test
    void unreadableVpsListAlsoRefusesToDecide() {
        appRunning("app1");
        when(vpsService.listMyInstances()).thenReturn(new VpsInstanceListResult(false, "backend down"));

        assertThrows(IllegalStateException.class, () -> service.findExistingBox("app1"));
    }

    @Test
    void aBoxWithNoOperatingSystemIsNotTreatedAsStillBuilding() {
        // Reachable only via attach: a server the customer owns that this tool never built.
        instanceState("vps-bare", "UNBUILT", "1.2.3.4");

        MoveToOwnedResult result = service.attach("app1", "vps-bare", null);

        assertEquals("BUILD_FAILED", result.status());
        assertTrue(result.message().contains("no operating system"), result.message());
        assertTrue(result.nextStep().contains("buildVpsInstance"));
        verify(deploymentService, never()).moveToOwned(any(), any(), any(), any());
    }

    @Test
    void findExistingBoxFallsBackToTheCustomersOwnVpsList() {
        appRunning("app1");                                       // C2 knows of no box
        myInstances(instance("vps-7", "unrelated.example"), instance("vps-own", "app1-owned.osir.app"));

        assertEquals("vps-own", service.findExistingBox("app1"));
    }

    @Test
    void findExistingBoxIsNullWhenTheCustomerOwnsNothingForThisApp() {
        appRunning("app1");
        myInstances(instance("vps-7", "something-else-owned.osir.app"));

        assertNull(service.findExistingBox("app1"));
    }

    // ---- DNS zone init (the "Zone not found in PowerDNS" the customer hit) ----

    @Test
    void missingZoneIsInitializedThenBound() {
        instanceState("vps-own", "COMPLETE", "1.2.3.4");
        when(deploymentService.moveToOwned(anyString(), anyString(), anyString(), any())).thenReturn(null);
        DnsRecordListResult empty = new DnsRecordListResult(true, "ok");
        empty.setRecords(List.of());
        when(dnsService.listRecords("fresh.com"))
                .thenReturn(new DnsRecordListResult(false, "zone not found"))   // first look: no zone
                .thenReturn(empty);                                             // after init: empty zone
        delegation("fresh.com", "ns1.osir.com", "ns3.osir.com");
        when(dnsService.initializeZone("fresh.com")).thenReturn(new DnsActionResult(true, "created"));
        when(dnsService.createRecord("fresh.com", "@", "A", "1.2.3.4", 3600, null))
                .thenReturn(new DnsRecordResult(true, "created"));

        MoveToOwnedResult result = service.attach("app1", "vps-own", "fresh.com");

        assertEquals(Boolean.TRUE, result.dnsBound());
        verify(dnsService).initializeZone("fresh.com");
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
