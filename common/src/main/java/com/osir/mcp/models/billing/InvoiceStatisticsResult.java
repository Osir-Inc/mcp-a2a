package com.osir.mcp.models.billing;

public class InvoiceStatisticsResult {
    private boolean success;
    private String message;
    private String totalPaid;
    private String totalPending;
    private String totalOverdue;
    private int paidCount;
    private int pendingCount;
    private int overdueCount;
    private String currency;

    public InvoiceStatisticsResult() {}

    public InvoiceStatisticsResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getTotalPaid() { return totalPaid; }
    public void setTotalPaid(String totalPaid) { this.totalPaid = totalPaid; }

    public String getTotalPending() { return totalPending; }
    public void setTotalPending(String totalPending) { this.totalPending = totalPending; }

    public String getTotalOverdue() { return totalOverdue; }
    public void setTotalOverdue(String totalOverdue) { this.totalOverdue = totalOverdue; }

    public int getPaidCount() { return paidCount; }
    public void setPaidCount(int paidCount) { this.paidCount = paidCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getOverdueCount() { return overdueCount; }
    public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
