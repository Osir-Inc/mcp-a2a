package com.osir.mcp.telemetry;

import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The masker is the only thing standing between a crash report and a live credential. It is a plain
 * function; test it as one. (The capture rule lives in ToolErrorsTest.)
 */
class ErrorReportingTest {

    @Test
    void masksEveryCredentialShapeKeepingFourCharacters() {
        assertEquals("session osk_AbCd********",
                ErrorReporting.mask("session osk_AbCd1234efgh5678"));
        assertEquals("key osir_live********",
                ErrorReporting.mask("key osir_livewxyz9876"));
        assertEquals("key osir_test_ab12********",
                ErrorReporting.mask("key osir_test_ab1234567890"));
        assertEquals("token spt_1a2b********",
                ErrorReporting.mask("token spt_1a2b3c4d5e6f"));
        assertEquals("stripe sk_live_51Hx******** and whsec_abcd********",
                ErrorReporting.mask("stripe sk_live_51HxAbCdEfGh and whsec_abcdefghijkl"));
        assertEquals("Authorization: Bearer ********",
                ErrorReporting.mask("Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.payload"));
    }

    @Test
    void leavesOrdinaryTextAlone() {
        assertEquals("Registration for example.com failed [DOMAIN_NOT_AVAILABLE]",
                ErrorReporting.mask("Registration for example.com failed [DOMAIN_NOT_AVAILABLE]"));
        assertNull(ErrorReporting.mask(null));
    }

    /** beforeSend has to reach every place a credential rides along, not just the message. */
    @Test
    void scrubsMessageExceptionValueAndExtras() {
        SentryEvent event = new SentryEvent();
        Message message = new Message();
        message.setMessage("poll failed for osk_AbCd1234efgh");
        event.setMessage(message);
        SentryException exception = new SentryException();
        exception.setValue("401 for Bearer eyJhbGciOiJSUzI1NiJ9.body.sig");
        event.setExceptions(List.of(exception));
        event.setExtra("action", "Top-up with spt_1a2b3c4d5e6f");

        ErrorReporting.scrub(event);

        assertEquals("poll failed for osk_AbCd********", event.getMessage().getMessage());
        assertEquals("401 for Bearer ********", event.getExceptions().get(0).getValue());
        assertEquals("Top-up with spt_1a2b********", event.getExtras().get("action"));
    }

    @Test
    void routeKeepsTheEndpointAndDropsTheCustomerResource() {
        assertEquals("/v1/domains/{domain}/dns/records/{id}",
                ErrorReporting.route("/v1/domains/example.com/dns/records/4213"));
        assertEquals("/v1/hosting/vps/instances/{id}/login",
                ErrorReporting.route("/v1/hosting/vps/instances/88/login"));
        assertEquals("/v1/apps/{uuid}/deploy",
                ErrorReporting.route("/v1/apps/3f2b1c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d/deploy"));
        assertEquals("/v1/mail/mailboxes/{domain}",
                ErrorReporting.route("/v1/mail/mailboxes/postmaster@example.com"));
        assertEquals("/v1/account/balance", ErrorReporting.route("/v1/account/balance"));
        assertEquals("/", ErrorReporting.route(null));
    }

    @Test
    void throttleLetsOneEventPerKeyPerMinuteThrough() {
        long t0 = 1_700_000_000_000L;
        String key = "GET /v1/account/balance:503";

        assertTrue(ErrorReporting.allow(key, t0), "first 5xx of an outage");
        assertFalse(ErrorReporting.allow(key, t0 + 59_000), "the thousandth tells us nothing new");
        assertTrue(ErrorReporting.allow(key, t0 + 61_000), "still broken a minute later: say so");
        assertTrue(ErrorReporting.allow("GET /v1/account/balance:500", t0), "a different status is a different fact");
    }

    /** The wiring itself: a DSN present means events are captured, absent means a silent no-op. */
    @Test
    void initEnablesSentryOnlyWithADsn() {
        try {
            new ErrorReporting().init(null);
            assertFalse(Sentry.isEnabled(), "no DSN configured in tests");

            // Port 1 so nothing is ever sent anywhere; the SDK drops failed sends silently.
            System.setProperty("osir.errors.dsn", "http://0123456789abcdef0123456789abcdef@127.0.0.1:1/2");
            new ErrorReporting().init(null);
            assertTrue(Sentry.isEnabled());
            ErrorReporting.capture("Availability check", new RuntimeException("boom"), 503);
        } finally {
            System.clearProperty("osir.errors.dsn");
            Sentry.close();
        }
    }
}
