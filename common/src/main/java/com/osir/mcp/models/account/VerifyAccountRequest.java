package com.osir.mcp.models.account;

/** POST /v1/public/account/verify — the code is the same token as the email link. */
public class VerifyAccountRequest {

    private String accountId;
    private String code;

    public VerifyAccountRequest() {}

    public VerifyAccountRequest(String accountId, String code) {
        this.accountId = accountId;
        this.code = code;
    }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
}
