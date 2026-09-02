package com.osir.mcp.models.account;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** 200 body of POST /v1/public/account/verify. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerifyAccountResponse {

    private String status; // ACTIVE
    private List<String> nextSteps;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<String> getNextSteps() { return nextSteps; }
    public void setNextSteps(List<String> nextSteps) { this.nextSteps = nextSteps; }
}
