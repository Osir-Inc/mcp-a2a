package com.osir.mcp.services;

import com.osir.mcp.clients.BillingBackendClient;
import com.osir.mcp.models.billing.*;
import com.osir.mcp.util.BackendError;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class BillingService {

    private static final Pattern SPT_PATTERN = Pattern.compile("^spt_[A-Za-z0-9_]{4,240}$");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Za-z]{3}$");
    private static final int DESCRIPTION_MAX = 500;

    /** Locally raised, so it cannot collide with a code the backend sends. */
    private static final String INVALID_AMOUNTS = "INVALID_AMOUNTS";
    private static final String INVALID_TOKEN_FORMAT = "INVALID_TOKEN_FORMAT";

    private static final String FALL_BACK =
            "Fall back to createPaymentSession and give the user the checkout URL - they still want the purchase.";

    private static final Logger LOG = Logger.getLogger(BillingService.class);

    @Inject
    @RestClient
    BillingBackendClient backendClient;

    @Inject
    AuthService authService;

    @ConfigProperty(name = "osir.payment.success-url", defaultValue = "https://lynx.osir.com/dashboard/billing/add-funds/success?session_id={CHECKOUT_SESSION_ID}")
    String paymentSuccessUrl;

    @ConfigProperty(name = "osir.payment.cancel-url", defaultValue = "https://lynx.osir.com/dashboard/billing/add-funds?cancelled=true")
    String paymentCancelUrl;

    public AccountBalanceResult getAccountBalance() {
        if (!authService.isAuthenticated()) {
            return new AccountBalanceResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            BalanceResponse response = backendClient.getAccountBalance(token);
            AccountBalanceResult result = new AccountBalanceResult(true, "Account balance retrieved successfully");
            result.setBalance(response.getBalance());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting account balance: %s", e.getMessage());
            return new AccountBalanceResult(false, "Failed to get account balance: " + e.getMessage());
        }
    }

    public InvoiceListResult listInvoices(String status, Integer page, Integer size) {
        if (!authService.isAuthenticated()) {
            return new InvoiceListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceListResponse response = backendClient.listInvoices(status, page, size, token);
            InvoiceListResult result = new InvoiceListResult(true, "Invoices retrieved successfully");
            result.setInvoices(response.getInvoices());
            result.setTotalCount(response.getTotalCount());
            result.setTotalPages(response.getTotalPages());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error listing invoices: %s", e.getMessage());
            return new InvoiceListResult(false, "Failed to list invoices: " + e.getMessage());
        }
    }

    public InvoiceDetailResult getInvoiceDetails(String invoiceId) {
        if (!authService.isAuthenticated()) {
            return new InvoiceDetailResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceDetailResponse response = backendClient.getInvoiceDetails(invoiceId, token);
            InvoiceDetailResult result = new InvoiceDetailResult(true, "Invoice details retrieved successfully");
            result.setId(response.getId());
            result.setInvoiceNumber(response.getInvoiceNumber());
            result.setStatus(response.getStatus());
            result.setTotalAmount(response.getTotalAmount());
            result.setCurrency(response.getCurrency());
            result.setInvoiceDate(response.getInvoiceDate());
            result.setDueDate(response.getDueDate());
            result.setPaidDate(response.getPaidDate());
            result.setItems(response.getItems());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting invoice details for %s: %s", invoiceId, e.getMessage());
            return new InvoiceDetailResult(false, "Failed to get invoice details: " + e.getMessage());
        }
    }

    public PaymentResult payInvoice(String invoiceId) {
        if (!authService.isAuthenticated()) {
            return new PaymentResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            PayInvoiceResponse response = backendClient.payInvoice(invoiceId, token);
            PaymentResult result = new PaymentResult(response.isSuccess(), response.getMessage());
            result.setInvoiceNumber(response.getInvoiceNumber());
            result.setAmountPaid(response.getAmountPaid());
            result.setRemainingBalance(response.getRemainingBalance());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error paying invoice %s: %s", invoiceId, e.getMessage());
            return new PaymentResult(false, "Payment failed: " + e.getMessage());
        }
    }

    public InvoiceStatisticsResult getInvoiceStatistics() {
        if (!authService.isAuthenticated()) {
            return new InvoiceStatisticsResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            InvoiceStatisticsResponse response = backendClient.getInvoiceStatistics(token);
            InvoiceStatisticsResult result = new InvoiceStatisticsResult(true, "Invoice statistics retrieved successfully");
            result.setTotalPaid(response.getTotalPaid());
            result.setTotalPending(response.getTotalPending());
            result.setTotalOverdue(response.getTotalOverdue());
            result.setPaidCount(response.getPaidCount());
            result.setPendingCount(response.getPendingCount());
            result.setOverdueCount(response.getOverdueCount());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting invoice statistics: %s", e.getMessage());
            return new InvoiceStatisticsResult(false, "Failed to get invoice statistics: " + e.getMessage());
        }
    }

    public PaymentSessionResult createPaymentSession(double amount, String currency) {
        if (!authService.isAuthenticated()) {
            return new PaymentSessionResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            PaymentSessionRequest request = new PaymentSessionRequest(
                    "stripe", amount, currency != null ? currency : "USD", paymentSuccessUrl, paymentCancelUrl);
            PaymentSessionResponse response = backendClient.createPaymentSession(request, token);
            PaymentSessionResult result = new PaymentSessionResult(response.isSuccess(), response.getMessage());
            result.setSessionId(response.getSessionId());
            result.setCheckoutUrl(response.getCheckoutUrl());
            result.setExpiresAt(response.getExpiresAt());
            result.setPollTool(response.getPollTool());
            result.setPollEndpoint(response.getPollEndpoint());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error creating payment session: %s", e.getMessage());
            return new PaymentSessionResult(false, "Failed to create payment session: " + e.getMessage());
        }
    }

    /**
     * Charges a Stripe Shared Payment Token the customer granted to OSIR and credits the balance.
     *
     * <p>{@code creditCents} is the NET (funding.fundNetCents); the card is charged that plus the
     * processing fee, which is why the token must have been minted for the GROSS
     * (funding.fundGrossCents).
     *
     * <p>The hard part is not the happy path, it is being honest about what happened when the call
     * does not come back cleanly - see {@link #outcomeIsUnknown}.
     */
    public TopUpResult fundBalanceWithSharedPaymentToken(String sharedPaymentToken, int creditCents,
                                                         String currency, Integer maxChargeCents,
                                                         String description) {
        if (!authService.isAuthenticated()) {
            return new TopUpResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        TopUpResult invalid = validate(sharedPaymentToken, creditCents, currency, maxChargeCents);
        if (invalid != null) {
            return invalid;
        }

        String ccy = currency != null ? currency.toUpperCase(Locale.ROOT) : "USD";
        String bearer;
        try {
            bearer = authService.getCurrentToken();
        } catch (Exception e) {
            // Deliberately not routed through topUpFailure: nothing was sent, so the outcome is not
            // in doubt and the agent must not be told to poll for a charge that never left.
            LOG.errorf(e, "Could not read the caller's token for a top-up: %s", e.getMessage());
            return new TopUpResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        TopUpRequest request = new TopUpRequest(sharedPaymentToken, creditCents, ccy, maxChargeCents,
                truncate(description));
        String key = idempotencyKey(sharedPaymentToken, creditCents, ccy);

        TopUpResponse response;
        try {
            // Only the call itself is guarded. Mapping its result below must not be mistaken for a
            // transport failure, or a successful charge gets reported as an unknown outcome.
            response = backendClient.createTopUp(key, request, bearer);
        } catch (Exception e) {
            return topUpFailure(e);
        }
        return mapTopUp(response);
    }

    /** Everything that can be refused without spending a round trip, let alone a charge. */
    private TopUpResult validate(String sharedPaymentToken, int creditCents, String currency,
                                 Integer maxChargeCents) {
        if (sharedPaymentToken == null || !SPT_PATTERN.matcher(sharedPaymentToken).matches()) {
            return refuse(INVALID_TOKEN_FORMAT, "Not a Stripe Shared Payment Token (expected 'spt_...').",
                    "Mint a token against the networkId from the funding block of the failure you are"
                    + " responding to, or fall back to createPaymentSession.");
        }
        if (creditCents < 1) {
            return refuse(INVALID_AMOUNTS, "creditCents must be at least 1.",
                    "Use funding.fundNetCents from the failure you are responding to.");
        }
        if (maxChargeCents != null && maxChargeCents < creditCents) {
            // The documented way to get this wrong: mint for the net and send the gross. Stripe then
            // declines for exceeding the token limit, which reads like a token bug and is arithmetic.
            // Caught here because the backend would otherwise burn a single-use token to say so.
            return refuse(INVALID_AMOUNTS,
                    "maxChargeCents (" + maxChargeCents + ") is below creditCents (" + creditCents
                    + "), which is impossible: the gross always covers the net plus the processing fee.",
                    "You have the two amounts the wrong way round. creditCents is the NET"
                    + " (funding.fundNetCents) and the token is minted for the GROSS"
                    + " (funding.fundGrossCents) - copy funding.stripe.usageLimits verbatim.");
        }
        if (currency != null && !CURRENCY_PATTERN.matcher(currency).matches()) {
            return refuse(INVALID_AMOUNTS, "currency must be a 3-letter ISO 4217 code, e.g. 'USD'.",
                    "Use funding.currency from the failure you are responding to.");
        }
        return null;
    }

    private static TopUpResult refuse(String code, String message, String nextStep) {
        TopUpResult result = new TopUpResult(false, message);
        result.setStatus("failed");
        result.setErrorCode(code);
        result.setNextStep(nextStep);
        return result;
    }

    private static String truncate(String description) {
        return description == null || description.length() <= DESCRIPTION_MAX
                ? description
                : description.substring(0, DESCRIPTION_MAX);
    }

    /**
     * One key per logical attempt, reused across retries - a fresh UUID per HTTP attempt would
     * defeat the mechanism and risk a double charge. Deriving it from the token and the amount gets
     * both halves for free: the same attempt replays the same key, and a re-minted token (the one
     * case the backend demands a new key, SPT_LIMIT_TOO_LOW) changes it automatically.
     *
     * <p>Deliberately not keyed on maxChargeCents or description. The triple below is what the
     * backend fingerprints, and widening the key would mean an agent that merely omitted the
     * optional maxChargeCents on a retry generated a new key - which is the double charge this
     * exists to prevent.
     */
    static String idempotencyKey(String sharedPaymentToken, int creditCents, String currency) {
        String seed = "osir-topup:" + sharedPaymentToken + ':' + creditCents + ':' + currency;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private TopUpResult mapTopUp(TopUpResponse response) {
        // A 2xx carrying success:false is not a shape the contract describes, but guessing
        // "credited" on a money path is not an option either.
        if (response != null && !response.isSuccess()) {
            return refuse(null, "Top-up was not completed: "
                    + (response.getMessage() != null ? response.getMessage() : "no reason given"), FALL_BACK);
        }

        TopUpResponse.Data data = response != null ? response.getData() : null;
        String status = data != null && data.getStatus() != null
                ? data.getStatus().toLowerCase(Locale.ROOT)
                : "unknown";

        TopUpResult result = new TopUpResult(true, null);
        result.setStatus(status);
        if (data != null) {
            result.setSessionId(data.getSessionId());
            result.setCreditedCents(data.getCreditedCents());
            result.setChargedCents(data.getChargedCents());
            result.setProcessingFeeCents(data.getProcessingFeeCents());
            result.setCurrency(data.getCurrency());
            result.setBalanceCents(data.getBalanceCents());
            result.setPollEndpoint(data.getPollEndpoint());
        }

        if ("completed".equals(status)) {
            result.setMessage("Balance funded.");
            result.setNextStep("Credited. Tell the user both numbers - the gross is what appears on"
                    + " their statement - then retry the purchase.");
        } else {
            // 202: the card may already have been charged. Saying "funded" here invites the agent to
            // retry the purchase, find it still fails, and start over with a second token.
            result.setMessage("Charge submitted; the outcome is not yet known."
                    + " The card may already have been charged.");
            result.setNextStep(pollInstead(data != null ? data.getPollEndpoint() : null));
        }
        return result;
    }

    /**
     * The agent's instruction whenever we cannot say whether the card was charged. It must not
     * re-mint, must not change the amount, and must not send the user to hosted checkout - each of
     * those is a way to charge someone twice.
     */
    private static String pollInstead(String pollEndpoint) {
        return "The outcome is UNKNOWN - the card may already have been charged. Do NOT mint a new"
                + " token, do NOT change the amount, and do NOT fall back to createPaymentSession."
                + " Poll " + (pollEndpoint != null ? pollEndpoint : "getPaymentTransactions")
                + " until it reads completed or failed; a reconciler settles it within 15 minutes."
                + " Calling this tool again with the identical token, amount and currency is safe -"
                + " it replays the same idempotency key rather than charging again.";
    }

    /**
     * Turns a failed call into the one thing the agent should do about it.
     *
     * <p>The classification matters more than the mapping: a timeout or a 5xx leaves the charge in
     * doubt, and the default advice - fall back to hosted checkout - would have the customer pay a
     * second time for a card that may already have been debited.
     */
    private TopUpResult topUpFailure(Exception e) {
        BackendError error = BackendError.of(e);
        // Never log the token or the request body: the token is a bearer credential.
        LOG.errorf("Shared payment token top-up failed: status=%d code=%s message=%s",
                error.status(), error.code(), error.message());

        if (outcomeIsUnknown(error)) {
            TopUpResult result = new TopUpResult(false, "Top-up outcome unknown: " + error.message());
            result.setStatus("unknown");
            result.setErrorCode(error.code());
            result.setNextStep(pollInstead(null));
            return result;
        }

        String next = switch (error.code() != null ? error.code() : "") {
            case "AMOUNT_BELOW_MINIMUM" -> "Raise creditCents to details.minimumCreditCents and call again.";
            case "SPT_LIMIT_TOO_LOW" -> "Mint a NEW token for details.requiredChargeCents, then call again with it.";
            case "SPT_EXPIRED", "SPT_DEACTIVATED" -> "Mint a fresh token - they are single-use - and call again.";
            case "CURRENCY_MISMATCH" -> "Call again with details.ledgerCurrency.";
            case "IDEMPOTENCY_KEY_REUSED" -> "Mint a fresh token and call again; that changes the key.";
            case "CARD_DECLINED" -> "The card was refused and nothing was charged. Tell the user plainly"
                    + " and stop: do not try another token or another amount.";
            case "IDEMPOTENCY_KEY_REQUIRED", "UNSUPPORTED_FUNDING_SOURCE" ->
                    "Bug in this tool, not in your call - report it. " + FALL_BACK;
            default -> FALL_BACK;
        };

        TopUpResult result = new TopUpResult(false,
                "Top-up failed: " + error.message() + (error.details() != null ? " " + error.details() : ""));
        result.setStatus("failed");
        result.setErrorCode(error.code());
        result.setNextStep(next);
        return result;
    }

    /**
     * Whether the card may already have been charged.
     *
     * <p>Only a definite answer from the backend is a definite answer. No response at all is a
     * timeout or a reset - the request may have landed and been acted on. A 5xx is the backend
     * falling over, possibly after it called Stripe; the one exception is SPT_NOT_CONFIGURED, which
     * it raises before going anywhere near a charge. A 409 says another attempt is still in flight.
     */
    private static boolean outcomeIsUnknown(BackendError error) {
        if (!error.hasResponse() || "REQUEST_IN_PROGRESS".equals(error.code())) {
            return true;
        }
        return error.isServerError() && !"SPT_NOT_CONFIGURED".equals(error.code());
    }

    public TransactionListResult getPaymentTransactions(Integer page, Integer size) {
        if (!authService.isAuthenticated()) {
            return new TransactionListResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            TransactionListResponse response = backendClient.getPaymentTransactions(page, size, token);
            TransactionListResult result = new TransactionListResult(true, "Payment transactions retrieved successfully");
            result.setTransactions(response.getTransactions());
            result.setTotalCount(response.getTotalCount());
            result.setTotalPages(response.getTotalPages());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting payment transactions: %s", e.getMessage());
            return new TransactionListResult(false, "Failed to get payment transactions: " + e.getMessage());
        }
    }

    public FeePreviewResult previewPaymentFees(double amount, String currency) {
        if (!authService.isAuthenticated()) {
            return new FeePreviewResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            FeePreviewResponse response = backendClient.previewPaymentFees(amount, currency, token);
            FeePreviewResult result = new FeePreviewResult(true, "Fee preview retrieved successfully");
            result.setAmount(response.getAmount());
            result.setFee(response.getFee());
            result.setTotal(response.getTotal());
            result.setCurrency(response.getCurrency());
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error previewing payment fees: %s", e.getMessage());
            return new FeePreviewResult(false, "Failed to preview fees: " + e.getMessage());
        }
    }

    @CacheResult(cacheName = "domain-pricing")
    public DomainPricingResult getDomainPricing(String extension) {
        if (!authService.isAuthenticated()) {
            return new DomainPricingResult(false, "Authentication required. Please use loginWithDevice to authenticate.");
        }

        try {
            String token = authService.getCurrentToken();
            List<PricingEntry> pricing = backendClient.getDomainPricing(extension, token);
            DomainPricingResult result = new DomainPricingResult(true, "Domain pricing retrieved successfully");
            result.setPricing(pricing);
            return result;
        } catch (Exception e) {
            LOG.errorf(e, "Error getting domain pricing: %s", e.getMessage());
            return new DomainPricingResult(false, "Failed to get domain pricing: " + e.getMessage());
        }
    }
}
