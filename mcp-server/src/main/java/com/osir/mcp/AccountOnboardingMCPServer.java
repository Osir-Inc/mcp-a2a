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
 * Anonymous account onboarding (backend v2.11.0), the autonomous-agent path to an ACTIVE
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

    // ponytail: per-connection limit only, a churny client gets fresh buckets. The backend's
    // per-client/per-account velocity limits (B.3) are the authoritative abuse guard.
    private void rateLimit(McpConnection connection, String what) {
        if (!rateLimiter.tryAcquire(connection.id(), DestructiveOpRateLimiter.Bucket.DESTRUCTIVE)) {
            throw new ToolCallException("Too many " + what + " attempts. Wait a minute and retry.");
        }
    }

    @Tool(description = """
            createAccount: Create a new OSIR customer account; step 1 of onboarding, no authentication \
            required. The contact must be the PRINCIPAL's real ICANN registrant contact (the \
            human or business the account is for), never the AI agent itself. Sends a \
            verification email; complete via verifyAccount with the emailed code. While \
            PENDING_VERIFICATION the account can search, quote and fund; billable actions need \
            ACTIVE. Calling again for a PENDING account re-sends the verification email.""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Create account",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public CreateAccountResponse createAccount(
            @ToolArg(description = "Account login email; valid mailbox that receives the verification code.") String email,
            @ToolArg(description = "Account type: INDIVIDUAL or ORGANIZATION.") String accountType,
            @ToolArg(description = "The principal's real ICANN registrant contact (firstName, lastName, email, phone, street1, city, country), never the AI agent itself.") CreateAccountRequest.Contact contact,
            @ToolArg(description = "Must be true; requires the principal's actual consent to the OSIR terms of service.") boolean acceptedTerms,
            @ToolArg(description = "Version of the OSIR terms the principal accepted, e.g. '2026-09'.") String termsVersion,
            @ToolArg(required = false, description = "Optional account password; if omitted the account is agent-managed until a password is set.") String password,
            @ToolArg(required = false, description = "Name of the AI agent acting for the principal, recorded for the audit trail.") String agentName,
            @ToolArg(required = false, description = "Vendor of the AI agent acting for the principal, recorded for the audit trail.") String agentVendor,
            @ToolArg(required = false, description = "Principal's own reference identifying who the agent acted for, recorded for the audit trail.") String principalReference,
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
            verifyAccount: Verify a newly created OSIR account with the code from the verification email; \
            step 2 of onboarding, no authentication required. The code is the same token as the \
            email link, so the principal can relay it to their agent. On success the account \
            becomes ACTIVE and billable actions are unlocked. If the code expired, call \
            createAccount again with the same email to get a fresh one.""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Verify account",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public VerifyAccountResponse verifyAccount(
            @ToolArg(description = "Account identifier returned by createAccount.") String accountId,
            @ToolArg(description = "Verification code from the email sent by createAccount.") String code,
            McpConnection connection) {
        rateLimit(connection, "verification");
        try {
            return backendClient.verifyAccount(new VerifyAccountRequest(accountId, code));
        } catch (Exception e) {
            throw ToolErrors.toolError("Account verification", e);
        }
    }
}
