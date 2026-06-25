package com.osir.mcp.models.billing;

import java.util.List;

public class TransactionListResponse {
    private List<TransactionSummary> transactions;
    private int totalCount;
    private int totalPages;

    public TransactionListResponse() {}

    public List<TransactionSummary> getTransactions() { return transactions; }
    public void setTransactions(List<TransactionSummary> transactions) { this.transactions = transactions; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
}
