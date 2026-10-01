package com.osir.mcp.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Renders the {@code funding} block the backend attaches to every insufficient-funds error (domain
 * register / renew, transfer initiate, VPS order) as the next step for the agent.
 *
 * <p>The contract is the block's <em>presence</em>, not the HTTP status or the error code - those
 * differ per path while {@code funding} does not.
 *
 * <p>ponytail: this reaches the client inside the failure message rather than as a typed field on
 * four separate *Result models. The reader is an LLM, and an instruction it can follow beats a data
 * blob it has to interpret. Give it a typed field when a non-LLM client needs one.
 */
public final class FundingHint {

    private FundingHint() {
    }

    /**
     * The standard failure message for a billable operation: the prefix, the backend's own reason,
     * and - when the customer is simply short of money - how to fix that without them.
     */
    public static String describe(String prefix, Throwable e) {
        BackendError error = BackendError.of(e);
        String hint = of(error);
        return hint == null
                ? prefix + ": " + error.message()
                : prefix + ": " + error.message() + " " + hint;
    }

    /** The next step implied by a parsed error, or null when it is not an insufficient-funds one. */
    public static String of(BackendError error) {
        JsonNode funding = error.funding();
        if (funding == null) {
            return null;
        }

        int net = funding.path("fundNetCents").asInt();
        int gross = funding.path("fundGrossCents").asInt();
        String currency = funding.path("currency").asText("USD");
        if (net <= 0 || gross < net) {
            return null; // Not a usable funding block; do not invent amounts for a money path.
        }

        // No stripe block = agent funding is off for this deployment, and the hosted checkout is the
        // only path. The backend omits it deliberately; treat it as "I cannot pay for this myself".
        JsonNode stripe = funding.path("stripe");
        String networkId = stripe.path("networkId").asText("");
        if (networkId.isBlank()) {
            return "The balance is short by " + money(net) + " " + currency
                    + ". Agent funding is not available here: call createPaymentSession(amount="
                    + money(net) + ", currency='" + currency
                    + "') and give the checkout URL to the user to pay.";
        }

        // Mint against the GROSS, send the NET. The other way round, Stripe declines the charge for
        // exceeding the token's limit, which reads like a token bug and is really arithmetic.
        return "The balance is short by " + money(net) + " " + currency
                + ". To pay for this without the user: mint a Stripe Shared Payment Token with"
                + " usage_limits {currency: '" + currency + "', max_amount: " + gross + "} (the GROSS -"
                + " it covers the " + money(gross - net) + " processing fee) against networkId '"
                + networkId + "', then call fundBalanceWithSharedPaymentToken(sharedPaymentToken=<the"
                + " token>, creditCents=" + net + ", currency='" + currency + "', maxChargeCents="
                + gross + ") and retry this operation. If you cannot mint a token, call"
                + " createPaymentSession(amount=" + money(net) + ", currency='" + currency
                + "') and give the checkout URL to the user instead.";
    }

    private static String money(int cents) {
        return BigDecimal.valueOf(cents, 2).toPlainString();
    }
}
