package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.models.billing.TopUpRequest;
import com.osir.mcp.models.billing.TopUpResponse;
import com.osir.mcp.models.billing.TopUpResult;
import com.osir.mcp.util.BackendError;
import com.osir.mcp.util.FundingHint;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The agent-funding path moves real money, and its failure modes are the interesting part: an
 * unknown outcome reported as a failure is how a card gets charged twice.
 */
class AgentFundingTest {

    @Mock BillingBackendClient backendClient;
    @Mock AuthService authService;
    @InjectMocks BillingService billingService;

    private static final String SPT = "spt_1A2b3C4d5E6f7G8h";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getCurrentToken()).thenReturn("Bearer test-token");
    }

    // ---------- idempotency ----------

    @Test
    void sameAttemptReusesTheKey_andAReMintedTokenChangesIt() {
        String key = BillingService.idempotencyKey(SPT, 1199, "USD");
        assertEquals(key, BillingService.idempotencyKey(SPT, 1199, "USD"), "a retry must replay the key");
        assertNotEquals(key, BillingService.idempotencyKey("spt_freshtoken123", 1199, "USD"),
                "SPT_LIMIT_TOO_LOW re-mints the token, which must produce a new key");
        assertNotEquals(key, BillingService.idempotencyKey(SPT, 1299, "USD"));
    }

    @Test
    void omittingTheOptionalMaxChargeOnARetryDoesNotChangeTheKey() {
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenReturn(completed());

        billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, "first");
        billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", null, "retry, no maxCharge");

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(backendClient, times(2)).createTopUp(keys.capture(), any(), anyString());
        assertEquals(keys.getAllValues().get(0), keys.getAllValues().get(1),
                "the key must not depend on an optional field, or a retry charges twice");
    }

    // ---------- the net/gross trap ----------

    @Test
    void sendsTheNetAndTheKeyAndReportsTheGross() {
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenReturn(completed());

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "usd", 1266, "example.com");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<TopUpRequest> body = ArgumentCaptor.forClass(TopUpRequest.class);
        verify(backendClient).createTopUp(key.capture(), body.capture(), anyString());

        assertEquals(BillingService.idempotencyKey(SPT, 1199, "USD"), key.getValue());
        assertEquals("shared_payment_token", body.getValue().getSource());
        assertEquals(1199, body.getValue().getCreditCents(), "creditCents is the NET");
        assertEquals(1266, body.getValue().getMaxChargeCents(), "maxChargeCents is the GROSS");
        assertEquals("USD", body.getValue().getCurrency(), "currency is normalised");
        assertTrue(result.isSuccess());
        assertEquals("completed", result.getStatus());
        assertEquals(1266, result.getChargedCents());
    }

    @Test
    void swappedAmountsAreRefusedWithoutBurningTheToken() {
        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1266, "USD", 1199, null);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_AMOUNTS", result.getErrorCode());
        assertTrue(result.getNextStep().contains("wrong way round"));
        verifyNoInteractions(backendClient);
    }

    @Test
    void localValidationNeverReachesTheBackend() {
        assertEquals("INVALID_TOKEN_FORMAT",
                billingService.fundBalanceWithSharedPaymentToken("pm_card_visa", 1199, "USD", 1266, null).getErrorCode());
        assertEquals("INVALID_AMOUNTS",
                billingService.fundBalanceWithSharedPaymentToken(SPT, 0, "USD", null, null).getErrorCode());
        assertEquals("INVALID_AMOUNTS",
                billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "US Dollars", null, null).getErrorCode());
        verifyNoInteractions(backendClient);
    }

    @Test
    void anOverlongDescriptionIsTruncatedRatherThanBounced() {
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenReturn(completed());

        billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, "x".repeat(900));

        ArgumentCaptor<TopUpRequest> body = ArgumentCaptor.forClass(TopUpRequest.class);
        verify(backendClient).createTopUp(anyString(), body.capture(), anyString());
        assertEquals(500, body.getValue().getDescription().length());
    }

    // ---------- outcome honesty: the double-charge guards ----------

    @Test
    void aTimeoutIsUnknownNotFailed_andMustNotRouteToHostedCheckout() {
        ProcessingException timeout = new ProcessingException(new SocketTimeoutException("read timed out"));
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(timeout);

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null);

        assertEquals("unknown", result.getStatus());
        assertTrue(result.getNextStep().contains("UNKNOWN"));
        assertFalse(instructsHostedCheckout(result.getNextStep()),
                "the card may already have been charged; a second payment path is a double charge");
        assertTrue(result.getNextStep().contains("identical token"),
                "the derived key makes an identical retry safe - say so");
    }

    @Test
    void aServerErrorIsUnknownToo_exceptTheOneRaisedBeforeStripeIsCalled() {
        WebApplicationException boom = error(500, "{\"error\":{\"message\":\"boom\"}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(boom);
        assertEquals("unknown",
                billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null).getStatus());

        reset(backendClient);
        WebApplicationException notConfigured =
                error(503, "{\"error\":{\"code\":\"SPT_NOT_CONFIGURED\",\"message\":\"no profile\"}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(notConfigured);
        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null);

        assertEquals("failed", result.getStatus(), "raised before any charge, so the outcome is definite");
        assertTrue(instructsHostedCheckout(result.getNextStep()));
    }

    @Test
    void aRequestAlreadyInFlightIsUnknown() {
        WebApplicationException inFlight =
                error(409, "{\"error\":{\"code\":\"REQUEST_IN_PROGRESS\",\"message\":\"in flight\"}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(inFlight);

        assertEquals("unknown",
                billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null).getStatus());
    }

    @Test
    void a202NeverClaimsTheBalanceWasFunded() {
        TopUpResponse processing = new TopUpResponse();
        processing.setSuccess(true);
        TopUpResponse.Data data = new TopUpResponse.Data();
        data.setStatus("processing");
        data.setPollEndpoint("/v1/payment/session/osir_tp_7c3f");
        processing.setData(data);
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenReturn(processing);

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null);

        assertEquals("processing", result.getStatus());
        assertFalse(result.getMessage().toLowerCase().contains("funded"));
        assertTrue(result.getNextStep().contains("/v1/payment/session/osir_tp_7c3f"));
        assertFalse(instructsHostedCheckout(result.getNextStep()));
    }

    @Test
    void a2xxCarryingSuccessFalseIsNotReadAsCredited() {
        TopUpResponse odd = new TopUpResponse();
        odd.setSuccess(false);
        odd.setMessage("ledger rejected the credit");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenReturn(odd);

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null);
        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("ledger rejected"));
    }

    // ---------- definite failures ----------

    @Test
    void aDeclinedCardIsReportedAndNotRetried() {
        WebApplicationException declined =
                error(402, "{\"error\":{\"code\":\"CARD_DECLINED\",\"message\":\"Your card was declined\"}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(declined);

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null);

        assertFalse(result.isSuccess());
        assertEquals("failed", result.getStatus());
        assertEquals("CARD_DECLINED", result.getErrorCode());
        assertTrue(result.getNextStep().contains("do not try another token"));
        assertFalse(instructsHostedCheckout(result.getNextStep()));
    }

    @Test
    void machineReadableDetailsReachTheAgent() {
        WebApplicationException low = error(400, "{\"error\":{\"code\":\"SPT_LIMIT_TOO_LOW\","
                + "\"message\":\"limit too low\",\"details\":{\"requiredChargeCents\":1266}}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(low);

        TopUpResult result = billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1200, null);
        assertTrue(result.getMessage().contains("requiredChargeCents"));
        assertTrue(result.getNextStep().contains("NEW token"));
    }

    @Test
    void anUnrecognisedDefiniteFailureFallsBackToTheHostedCheckout() {
        WebApplicationException disabled =
                error(403, "{\"error\":{\"code\":\"SPT_NOT_ENABLED\",\"message\":\"disabled\"}}");
        when(backendClient.createTopUp(anyString(), any(), anyString())).thenThrow(disabled);

        assertTrue(instructsHostedCheckout(
                billingService.fundBalanceWithSharedPaymentToken(SPT, 1199, "USD", 1266, null).getNextStep()));
    }

    // ---------- the funding block ----------

    @Test
    void theFundingHintCarriesTheNetworkIdAndMintsAgainstTheGross() {
        String hint = FundingHint.of(BackendError.of(error(402, """
                {"success":false,"error":"Insufficient funds. Required: 1499, Available: 300",
                 "errorCode":"INSUFFICIENT_FUNDS","funding":{
                  "shortfallCents":1199,"currency":"USD","fundNetCents":1199,"fundGrossCents":1266,
                  "stripe":{"networkId":"profile_test_abc","usageLimits":{"currency":"USD","maxAmount":1266}}}}""")));

        assertNotNull(hint);
        assertTrue(hint.contains("profile_test_abc"), "networkId must be read from the error, never hardcoded");
        assertTrue(hint.contains("max_amount: 1266"), "mint against the gross");
        assertTrue(hint.contains("creditCents=1199"), "send the net");
        assertTrue(hint.contains("0.67"), "the fee is the difference, not a guessed percentage");
    }

    @Test
    void describeKeepsTheBackendsOwnWording() {
        String message = FundingHint.describe("Registration failed", error(402, """
                {"error":"Insufficient funds. Required: 1499, Available: 300","funding":{
                 "currency":"USD","fundNetCents":1199,"fundGrossCents":1266,
                 "stripe":{"networkId":"profile_test_abc"}}}"""));

        assertTrue(message.startsWith("Registration failed: Insufficient funds. Required: 1499"),
                "the framework's 'Payment Required, status code 402' helps nobody");
        assertTrue(message.contains("fundBalanceWithSharedPaymentToken"));
    }

    @Test
    void withoutAStripeBlockTheHintPointsAtTheHumanCheckout() {
        String hint = FundingHint.of(BackendError.of(error(402,
                "{\"funding\":{\"currency\":\"USD\",\"fundNetCents\":1199,\"fundGrossCents\":1266}}")));
        assertTrue(hint.contains("createPaymentSession"));
        assertFalse(hint.contains("SharedPaymentToken"));
    }

    @Test
    void anOrdinaryErrorGetsNoHint() {
        assertNull(FundingHint.of(BackendError.of(error(400, "{\"error\":\"Domain already registered\"}"))));
        assertNull(FundingHint.of(BackendError.of(new RuntimeException("connection reset"))));
        assertEquals("Registration failed: boom",
                FundingHint.describe("Registration failed", new RuntimeException("boom")));
    }

    // ---------- the parser ----------

    @Test
    void backendErrorHandlesBothEnvelopesAndSurvivesRubbish() {
        BackendError structured = BackendError.of(error(400, "{\"error\":{\"code\":\"AMOUNT_BELOW_MINIMUM\","
                + "\"message\":\"too small\",\"details\":{\"minimumCreditCents\":50}}}"));
        assertEquals("AMOUNT_BELOW_MINIMUM", structured.code());
        assertEquals("too small", structured.message());
        assertTrue(structured.details().contains("50"));

        BackendError flat = BackendError.of(error(400, "{\"error\":\"nope\",\"errorCode\":\"REGISTRATION_FAILED\"}"));
        assertEquals("REGISTRATION_FAILED", flat.code());
        assertEquals("nope", flat.message());

        BackendError rubbish = BackendError.of(error(500, "<html>502 Bad Gateway</html>"));
        assertEquals("HTTP 500", rubbish.message());
        assertTrue(rubbish.isServerError());

        BackendError transport = BackendError.of(new ProcessingException(new SocketTimeoutException("read timed out")));
        assertFalse(transport.hasResponse(), "no response means the outcome is unknown, not failed");
        assertEquals("read timed out", transport.message());
    }

    @Test
    void theErrorBodyStaysReadableForASecondReader() {
        WebApplicationException e = error(402, "{\"error\":\"Insufficient funds\","
                + "\"funding\":{\"currency\":\"USD\",\"fundNetCents\":1199,\"fundGrossCents\":1266}}");

        assertEquals("Insufficient funds", BackendError.of(e).message());
        assertEquals("Insufficient funds", BackendError.of(e).message(),
                "the entity is buffered; a second reader must not get an empty body");
    }

    /** The mocks above would happily hide a response model that deserializes to all-nulls. */
    @Test
    void theBackendsOwn201Body() throws Exception {
        TopUpResponse r = new ObjectMapper().readValue("""
                {"success":true,"data":{"sessionId":"osir_tp_7c3f","status":"completed",
                 "creditedCents":1199,"chargedCents":1266,"processingFeeCents":67,"currency":"USD",
                 "balanceCents":1499,"paymentIntentId":"pi_1","settledBy":"inline",
                 "pollEndpoint":"/v1/payment/session/osir_tp_7c3f"}}""", TopUpResponse.class);

        assertEquals("completed", r.getData().getStatus());
        assertEquals(1199, r.getData().getCreditedCents());
        assertEquals(1266, r.getData().getChargedCents());
        assertEquals(1499, r.getData().getBalanceCents());
        assertEquals("/v1/payment/session/osir_tp_7c3f", r.getData().getPollEndpoint());
    }

    /**
     * The distinguishing sentence. "do NOT fall back to createPaymentSession" merely mentions the
     * tool; only this wording actually sends the customer off to pay a second time.
     */
    private static boolean instructsHostedCheckout(String nextStep) {
        return nextStep.contains("Fall back to createPaymentSession");
    }

    private static TopUpResponse completed() {
        TopUpResponse response = new TopUpResponse();
        response.setSuccess(true);
        TopUpResponse.Data data = new TopUpResponse.Data();
        data.setStatus("completed");
        data.setCreditedCents(1199);
        data.setChargedCents(1266);
        data.setBalanceCents(1499);
        response.setData(data);
        return response;
    }

    private static WebApplicationException error(int status, String body) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        // WebApplicationException builds its message from the status info; an unstubbed null NPEs.
        when(response.getStatusInfo()).thenReturn(Response.Status.fromStatusCode(status));
        when(response.hasEntity()).thenReturn(true);
        when(response.readEntity(String.class)).thenReturn(body);
        return new WebApplicationException(response);
    }
}
