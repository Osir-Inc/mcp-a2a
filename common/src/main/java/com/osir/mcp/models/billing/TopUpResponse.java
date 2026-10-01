package com.osir.mcp.models.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 201 (completed) / 202 (processing) body of POST /v1/payment/topups. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopUpResponse {
    private boolean success;
    private String message;
    private Data data;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String sessionId;
        private String status;
        private Integer creditedCents;
        private Integer chargedCents;
        private Integer processingFeeCents;
        private String currency;
        private Integer balanceCents;
        private String pollEndpoint;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Integer getCreditedCents() { return creditedCents; }
        public void setCreditedCents(Integer creditedCents) { this.creditedCents = creditedCents; }
        public Integer getChargedCents() { return chargedCents; }
        public void setChargedCents(Integer chargedCents) { this.chargedCents = chargedCents; }
        public Integer getProcessingFeeCents() { return processingFeeCents; }
        public void setProcessingFeeCents(Integer processingFeeCents) { this.processingFeeCents = processingFeeCents; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Integer getBalanceCents() { return balanceCents; }
        public void setBalanceCents(Integer balanceCents) { this.balanceCents = balanceCents; }
        public String getPollEndpoint() { return pollEndpoint; }
        public void setPollEndpoint(String pollEndpoint) { this.pollEndpoint = pollEndpoint; }
    }
}
