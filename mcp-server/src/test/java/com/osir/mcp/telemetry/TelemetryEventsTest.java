package com.osir.mcp.telemetry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.models.catalog.HostingBundleResponse;
import com.osir.mcp.models.confirmation.ConfirmationRequiredResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TelemetryEventsTest {

    @Test
    void stageMapping() {
        assertEquals("quoted", TelemetryEvents.stage("checkDomainAvailability", true, null));
        assertEquals("quoted", TelemetryEvents.stage("getHostingBundle", true, null));
        assertEquals("confirmed", TelemetryEvents.stage("executeConfirmedAction", true, null));
        assertEquals("deployed", TelemetryEvents.stage("osirSitePublish", true, null));
        assertEquals("staged", TelemetryEvents.stage("registerDomain", true, new ConfirmationRequiredResult()));
        assertNull(TelemetryEvents.stage("listDnsRecords", true, null));
        assertNull(TelemetryEvents.stage("checkDomainAvailability", false, null));
    }

    /** The live /v1/public/catalog/bundle payload shape (captured 2026-09-02) must map cleanly. */
    @Test
    void hostingBundleDeserializes() throws Exception {
        String json = """
                {"nextSteps":["Deploy a free site"],"domain":"example.com",
                 "options":{"appDeploy":{"available":true,"buildPrice":{"amount":0,"currency":"USD","taxIncluded":false}},
                            "mail":{"available":true,"packages":[{"name":"Basic","priceMonthlyCents":199}]},
                            "vps":{"recommended":[{"memoryMb":512,"price":{"amount":199,"currency":"USD","period":"MONTHLY"}}]}},
                 "unknownFutureField":42}
                """;
        HostingBundleResponse bundle = new ObjectMapper().readValue(json, HostingBundleResponse.class);
        assertEquals("example.com", bundle.getDomain());
        assertEquals(1, bundle.getNextSteps().size());
        assertTrue(bundle.getOptions().containsKey("vps"));
    }
}
