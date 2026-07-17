package com.osir.mcp;

import com.osir.mcp.models.account.AccountSummaryResult;
import com.osir.mcp.models.account.UserProfileResult;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.AccountService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@McpAudited
@RequiresAuth
@ApplicationScoped
public class AccountMCPServer {

    @Inject
    AccountService accountService;

    @Tool(description = "Get the authenticated user's profile and account information including name, email, organization, balance, and domain/VPS counts. Requires authentication.")
    public UserProfileResult getMyProfile(McpConnection connection) {
        try {
            return accountService.getMyProfile();
        } catch (Exception e) {
            Log.errorf(e, "Error getting user profile: %s", e.getMessage());
            return new UserProfileResult(false, "Failed to get user profile: " + e.getMessage());
        }
    }

    @Tool(description = "Get a comprehensive summary of the user's account: profile, balance, domain count, VPS count, and pending transfers. Requires authentication.")
    public AccountSummaryResult getAccountSummary(McpConnection connection) {
        try {
            return accountService.getAccountSummary();
        } catch (Exception e) {
            Log.errorf(e, "Error getting account summary: %s", e.getMessage());
            return new AccountSummaryResult(false, "Failed to get account summary: " + e.getMessage());
        }
    }
}
