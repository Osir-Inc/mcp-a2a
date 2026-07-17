package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsOrderResponse {
    private Long orderId;
    private Long invoiceId;
    private String invoiceNumber;
    private Double totalAmount;
    private VpsInstanceSummary instance;

    public VpsOrderResponse() {}

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }

    public VpsInstanceSummary getInstance() { return instance; }
    public void setInstance(VpsInstanceSummary instance) { this.instance = instance; }

    // Convenience accessors used by VpsService
    public boolean isSuccess() { return instance != null; }
    public String getMessage() { return instance != null ? "VPS ordered successfully" : "Order failed: no instance returned"; }
    public String getInstanceId() { return instance != null ? instance.getId() : null; }
    public String getHostname() { return instance != null ? instance.getHostname() : null; }
    public String getIpAddress() { return instance != null ? instance.getIpAddress() : null; }
    public String getStatus() { return instance != null ? instance.getStatus() : null; }
    public String getPackageName() { return instance != null ? instance.getPackageName() : null; }
    public String getLocation() { return instance != null ? instance.getLocation() : null; }
}
