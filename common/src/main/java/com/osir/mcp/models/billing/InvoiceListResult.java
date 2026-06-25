package com.osir.mcp.models.billing;

import java.util.List;

public class InvoiceListResult {
    private boolean success;
    private String message;
    private List<InvoiceSummary> invoices;
    private int totalCount;
    private int totalPages;

    public InvoiceListResult() {}

    public InvoiceListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<InvoiceSummary> getInvoices() { return invoices; }
    public void setInvoices(List<InvoiceSummary> invoices) { this.invoices = invoices; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
