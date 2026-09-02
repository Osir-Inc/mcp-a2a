package com.osir.mcp.models.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentSessionResponse {
    private boolean success;
    private String message;
    private Data data;

    public PaymentSessionResponse() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    public String getSessionId() { return data != null ? data.sessionId : null; }
    public String getCheckoutUrl() { return data != null ? data.checkoutUrl : null; }
    public String getExpiresAt() { return data != null ? data.expiresAt : null; }
    public String getPollTool() { return data != null ? data.pollTool : null; }
    public String getPollEndpoint() { return data != null ? data.pollEndpoint : null; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String sessionId;
        private String checkoutUrl;
        private String processor;
        private Double amount;
        private String currency;
        private String expiresAt;
        // Backend v2.12: how to poll for completion after handing the checkout URL to the human
        private String pollTool;
        private String pollEndpoint;

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getCheckoutUrl() { return checkoutUrl; }
        public void setCheckoutUrl(String checkoutUrl) { this.checkoutUrl = checkoutUrl; }

        public String getProcessor() { return processor; }
        public void setProcessor(String processor) { this.processor = processor; }

        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getExpiresAt() { return expiresAt; }
        public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }

        public String getPollTool() { return pollTool; }
        public void setPollTool(String pollTool) { this.pollTool = pollTool; }

        public String getPollEndpoint() { return pollEndpoint; }
        public void setPollEndpoint(String pollEndpoint) { this.pollEndpoint = pollEndpoint; }
    }
}
