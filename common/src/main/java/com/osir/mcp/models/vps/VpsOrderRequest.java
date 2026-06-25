package com.osir.mcp.models.vps;

public class VpsOrderRequest {
    private String packageId;
    private String hostname;
    private String paymentTerm;
    private String operatingSystem;

    public VpsOrderRequest() {}

    public VpsOrderRequest(String packageId, String hostname, String paymentTerm, String operatingSystem) {
        this.packageId = packageId;
        this.hostname = hostname;
        this.paymentTerm = paymentTerm;
        this.operatingSystem = operatingSystem;
    }

    public String getPackageId() { return packageId; }
    public void setPackageId(String packageId) { this.packageId = packageId; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public String getOperatingSystem() { return operatingSystem; }
    public void setOperatingSystem(String operatingSystem) { this.operatingSystem = operatingSystem; }
}
