package com.osir.mcp.telemetry;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import io.sentry.protocol.SentryException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Crash reporting to GlitchTip, which speaks the Sentry protocol (see the registrar's
 * docs/glitchtip-mcp-integration.md).
 *
 * <p>Three things report, and nothing else:
 * <ul>
 * <li>{@link BackendErrorCaptureFilter} — every backend 5xx, on every REST client, including the
 *     ones a tool swallows into a {@code success:false} result. This is the one that covers the
 *     whole surface: there is no single error chokepoint in the tool layer.</li>
 * <li>{@link com.osir.mcp.services.ToolErrors} — transport and parse failures, which never reach a
 *     response filter because there is no response.</li>
 * <li>The SDK's own default integrations, which capture an uncaught exception on any thread.</li>
 * </ul>
 *
 * <p>A 4xx is the user or the business, never an incident, and the per-route throttle below keeps a
 * backend outage from spending the monthly event quota we share with the backend and the panel. A
 * reporting tool that is mostly noise gets muted within a week, and then it is worse than nothing
 * because people believe they are covered.
 *
 * <p>Nothing is sent unless {@code osir.errors.dsn} is set, so dev and tests are a no-op.
 */
@ApplicationScoped
public class ErrorReporting {

    private static final Logger LOG = Logger.getLogger(ErrorReporting.class);

    /**
     * Every credential shape this server handles. They travel in exactly the places a crash report
     * captures: tool arguments, messages, stack frames. Each pattern keeps the first four characters
     * after the prefix, enough to match a report against a Stripe entry or a support ticket and
     * useless to anyone who finds it. The backend masks the same shapes in
     * {@code com.osir.logging.SecretMaskingLogFilter}; keep the two in rough step.
     */
    private static final List<Pattern> CREDENTIALS = List.of(
            // Session keys the device-login flow mints.
            Pattern.compile("\\b(osk_[A-Za-z0-9]{4})[A-Za-z0-9_-]{4,}"),
            // Our API keys, including the osir_test_ variants.
            Pattern.compile("\\b(osir(?:_test)?_[A-Za-z0-9]{4})[A-Za-z0-9_-]{4,}"),
            // Stripe shared payment tokens: single-use, but spendable until consumed.
            Pattern.compile("\\b(spt_[A-Za-z0-9]{4})[A-Za-z0-9]{4,}"),
            // Stripe keys and webhook signing secrets, in case one is ever echoed back.
            Pattern.compile("\\b((?:sk_live|sk_test|rk_live|rk_test|whsec)_[A-Za-z0-9]{4})[A-Za-z0-9]{4,}"),
            // Bearer tokens: this server holds real OAuth access tokens.
            Pattern.compile("(Bearer\\s+)[A-Za-z0-9._~+/-]{16,}=*", Pattern.CASE_INSENSITIVE));

    // ponytail: StartupEvent is the earliest hook an app bean gets, so a failure before CDI starts
    // (bad config, missing extension) still only reaches the container log. Good enough: those
    // failures are loud and happen on deploy, not in front of a user.
    void init(@Observes StartupEvent ev) {
        String dsn = config("osir.errors.dsn", "");
        if (dsn.isBlank()) {
            LOG.debug("Error reporting disabled (osir.errors.dsn is unset)");
            return;
        }
        Sentry.init(options -> {
            options.setDsn(dsn);
            options.setEnvironment(config("osir.errors.environment", "production"));
            // Without a release you cannot tell which version a crash started in.
            options.setRelease(config("quarkus.application.version", "unknown"));
            // Errors only. Tracing would multiply event volume for no benefit at our size, and the
            // monthly ceiling is shared with the backend and the panel.
            options.setTracesSampleRate(0.0);
            // Breadcrumbs are automatic log capture by another name: they would carry the
            // credentials above with none of the masking below.
            options.setMaxBreadcrumbs(0);
            options.setBeforeSend((event, hint) -> scrub(event));
        });
        LOG.infof("Error reporting enabled (environment %s, release %s)",
                config("osir.errors.environment", "production"),
                config("quarkus.application.version", "unknown"));
    }

    /**
     * The SDK sends in the background, so a JVM that exits first drops whatever is queued, and the
     * errors thrown on the way down are the ones you most want. Same reason the doc's smoke test
     * needs flush() to show anything at all.
     */
    void shutdown(@Observes ShutdownEvent ev) {
        Sentry.flush(2000);
    }

    /**
     * Report a failure worth a human look. Never throws: reporting must not change what the tool
     * returns. A no-op until {@link #init} runs with a DSN.
     */
    public static void capture(String action, Throwable t, int backendStatus) {
        // Key on the exception type, not the action: the action carries a domain name, so it would
        // make the throttle key unbounded and defeat it.
        if (!allow(t.getClass().getName() + ':' + backendStatus, System.currentTimeMillis())) return;
        try {
            Sentry.withScope(scope -> {
                scope.setTag("backend.status", backendStatus > 0 ? String.valueOf(backendStatus) : "none");
                // The failing tool is named by the stack trace; the action is the human label.
                scope.setExtra("action", action);
                Sentry.captureException(t);
            });
        } catch (Exception e) {
            LOG.debugf(e, "Could not report error for %s", action);
        }
    }

    /**
     * Report a backend 5xx seen by a REST client. There is no exception here and often no tool
     * frame either, so this is a message keyed by route: during an outage you want one group per
     * endpoint, not one per customer resource.
     */
    public static void captureBackendFailure(String method, String path, int status) {
        String route = method + ' ' + route(path);
        if (!allow(route + ':' + status, System.currentTimeMillis())) return;
        try {
            Sentry.withScope(scope -> {
                scope.setTag("backend.status", String.valueOf(status));
                scope.setTag("backend.route", route);
                Sentry.captureMessage("Backend " + status + " on " + route, SentryLevel.ERROR);
            });
        } catch (Exception e) {
            LOG.debugf(e, "Could not report backend failure for %s", route);
        }
    }

    /**
     * {@code /v1/domains/example.com/dns/records/4213} becomes
     * {@code /v1/domains/{domain}/dns/records/{id}}: one GlitchTip group per endpoint, and no
     * customer's domain in an ops system that keeps events for months.
     */
    static String route(String path) {
        if (path == null || path.isBlank()) return "/";
        return path.replaceAll("/[^/]+\\.[a-zA-Z]{2,}(?=/|$)", "/{domain}")
                .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F-]{20,}(?=/|$)", "/{uuid}")
                .replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    /**
     * One event per key per minute. A backend outage means every in-flight tool call sees a 5xx, and
     * the first one tells you everything the thousandth would.
     */
    static boolean allow(String key, long now) {
        Long last = LAST_SENT.get(key);
        if (last != null && now - last < THROTTLE_MS) return false;
        LAST_SENT.put(key, now);
        return true;
    }

    // ponytail: never cleaned, because the keys are routes and exception types - bounded by the code,
    // not by traffic. If a key ever embeds anything per-request, this grows and needs a sweep.
    private static final Map<String, Long> LAST_SENT = new ConcurrentHashMap<>();
    private static final long THROTTLE_MS = 60_000L;

    static SentryEvent scrub(SentryEvent event) {
        Message message = event.getMessage();
        if (message != null) {
            message.setMessage(mask(message.getMessage()));
            message.setFormatted(mask(message.getFormatted()));
        }
        List<SentryException> exceptions = event.getExceptions();
        if (exceptions != null) {
            for (SentryException ex : exceptions) {
                ex.setValue(mask(ex.getValue()));
            }
        }
        Map<String, Object> extras = event.getExtras();
        if (extras != null) {
            // Copy the keys: setExtra writes to the same map we are reading.
            for (String key : List.copyOf(extras.keySet())) {
                Object value = extras.get(key);
                if (value instanceof String s) event.setExtra(key, mask(s));
            }
        }
        return event;
    }

    static String mask(String value) {
        if (value == null) return null;
        String masked = value;
        for (Pattern pattern : CREDENTIALS) {
            masked = pattern.matcher(masked).replaceAll("$1********");
        }
        return masked;
    }

    private static String config(String name, String fallback) {
        return ConfigProvider.getConfig().getOptionalValue(name, String.class).orElse(fallback);
    }
}
