package com.osir.mcp.models.vps;

public class VpsPaymentTermRequest {
    private String paymentTerm;

    public VpsPaymentTermRequest() {}

    public VpsPaymentTermRequest(String paymentTerm) {
        this.paymentTerm = paymentTerm;
    }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }
}
