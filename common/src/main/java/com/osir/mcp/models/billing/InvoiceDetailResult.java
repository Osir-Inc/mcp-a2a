package com.osir.mcp.models.billing;

import java.util.List;

public class InvoiceDetailResult {
    private boolean success;
    private String message;
    private String id;
    private String invoiceNumber;
    private String status;
    private String totalAmount;
    private String currency;
    private String invoiceDate;
    private String dueDate;
    private String paidDate;
    private List<InvoiceItemDetail> items;

    public InvoiceDetailResult() {}

    public InvoiceDetailResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTotalAmount() { return totalAmount; }
    public void setTotalAmount(String totalAmount) { this.totalAmount = totalAmount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(String invoiceDate) { this.invoiceDate = invoiceDate; }

    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public String getPaidDate() { return paidDate; }
    public void setPaidDate(String paidDate) { this.paidDate = paidDate; }

    public List<InvoiceItemDetail> getItems() { return items; }
    public void setItems(List<InvoiceItemDetail> items) { this.items = items; }
}
