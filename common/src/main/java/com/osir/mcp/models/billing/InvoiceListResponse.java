package com.osir.mcp.models.billing;

import java.util.List;

public class InvoiceListResponse {
    private List<InvoiceSummary> invoices;
    private int totalCount;
    private int totalPages;

    public InvoiceListResponse() {}

    public List<InvoiceSummary> getInvoices() { return invoices; }
    public void setInvoices(List<InvoiceSummary> invoices) { this.invoices = invoices; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
