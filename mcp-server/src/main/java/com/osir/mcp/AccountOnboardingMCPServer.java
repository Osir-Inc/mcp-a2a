package com.osir.mcp;

import com.osir.mcp.clients.DomainBackendClient;
import com.osir.mcp.models.account.CreateAccountRequest;
import com.osir.mcp.models.account.CreateAccountResponse;
import com.osir.mcp.models.account.VerifyAccountRequest;
import com.osir.mcp.models.account.VerifyAccountResponse;
import com.osir.mcp.security.DestructiveOpRateLimiter;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.services.ToolErrors;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Anonymous account onboarding (backend v2.11.0) — the autonomous-agent path to an ACTIVE
 * account with no browser: createAccount → email verification code → verifyAccount.
 * Deliberately NOT @RequiresAuth: these tools exist precisely for callers with no account yet.
 */
@McpAudited
@ApplicationScoped
public class AccountOnboardingMCPServer {

    @Inject
    @RestClient
    DomainBackendClient backendClient;

    @Inject
    DestructiveOpRateLimiter rateLimiter;

    // ponytail: per-connection limit only — a churny client gets fresh buckets. The backend's
    // per-client/per-account velocity limits (B.3) are the authoritative abuse guard.
    private void rateLimit(McpConnection connection, String what) {
        if (!rateLimiter.tryAcquire(connection.id(), DestructiveOpRateLimiter.Bucket.DESTRUCTIVE)) {
            throw new ToolCallException("Too many " + what + " attempts. Wait a minute and retry.");
        }
    }

    @Tool(description = """
            Create a new OSIR customer account. No authentication required — this is step 1 of \
            onboarding. The contact must be the PRINCIPAL's real ICANN registrant contact (the \
            human or business the account is for), never the AI agent itself. Sends a \
            verification email to the account email; the principal relays the emailed code to \
            complete verification via verifyAccount. acceptedTerms must be true and requires the \
            principal's actual consent to the OSIR terms (state the termsVersion you accepted). \
            While PENDING_VERIFICATION the account can search, quote and fund; billable actions \
            (registerDomain execution) need ACTIVE. Calling again for a PENDING account re-sends \
            the verification email. Required: email, accountType (INDIVIDUAL|ORGANIZATION), \
            contact {firstName, lastName, email, phone (+CC.number), street1, city, country \
            (2-letter)}, acceptedTerms, termsVersion. Optional: password, agentName, agentVendor, \
            principalReference (audit trail of which agent acted for whom). \
            Next step: verifyAccount with the emailed code.""")
    public CreateAccountResponse createAccount(
            String email,
            String accountType,
            CreateAccountRequest.Contact contact,
            boolean acceptedTerms,
            String termsVersion,
            @ToolArg(required = false) String password,
            @ToolArg(required = false) String agentName,
            @ToolArg(required = false) String agentVendor,
            @ToolArg(required = false) String principalReference,
            McpConnection connection) {
        rateLimit(connection, "account creation");
        if (!acceptedTerms) {
            throw new ToolCallException("acceptedTerms must be true. Ask the principal to review "
                    + "the OSIR terms of service and confirm acceptance, then retry with "
                    + "acceptedTerms:true and the termsVersion they accepted.");
        }
        CreateAccountRequest request = new CreateAccountRequest();
        request.setEmail(email);
        request.setPassword(password);
        request.setAccountType(accountType);
        request.setContact(contact);
        request.setAcceptedTerms(true);
        request.setTermsVersion(termsVersion);
        if (agentName != null || agentVendor != null || principalReference != null) {
            request.setAgentMetadata(new CreateAccountRequest.AgentMetadata(
                    agentName, agentVendor, principalReference));
        }
        try {
            return backendClient.createAccount(request);
        } catch (Exception e) {
            throw ToolErrors.toolError("Account creation", e);
        }
    }

    @Tool(description = """
            Verify a newly created OSIR account with the code from the verification email — \
            step 2 of onboarding, no authentication required. The code is the same token as the \
            email link, so the principal can relay it to their agent. On success the account \
            becomes ACTIVE and billable actions are unlocked. If the code expired, call \
            createAccount again with the same email to get a fresh one. \
            Required: accountId (from createAccount), code.""")
    public VerifyAccountResponse verifyAccount(String accountId, String code, McpConnection connection) {
        rateLimit(connection, "verification");
        try {
            return backendClient.verifyAccount(new VerifyAccountRequest(accountId, code));
        } catch (Exception e) {
            throw ToolErrors.toolError("Account verification", e);
        }
    }
}
