package com.osir.mcp.models.billing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

// API shape: { statistics: { paidCount, pendingCount, overdueCount,
//              totalAmountPaid, totalAmountPending, totalAmountOverdue } }
// All totalAmount* fields are in cents.
@JsonIgnoreProperties(ignoreUnknown = true)
public class InvoiceStatisticsResponse {

    private Statistics statistics;

    public Statistics getStatistics() { return statistics; }
    public void setStatistics(Statistics statistics) { this.statistics = statistics; }

    // --- Convenience accessors used by BillingService ---
    public String getTotalPaid() {
        return statistics != null && statistics.totalAmountPaid != null
                ? String.format(java.util.Locale.US, "%.2f", statistics.totalAmountPaid / 100.0) : null;
    }
    public String getTotalPending() {
        return statistics != null && statistics.totalAmountPending != null
                ? String.format(java.util.Locale.US, "%.2f", statistics.totalAmountPending / 100.0) : null;
    }
    public String getTotalOverdue() {
        return statistics != null && statistics.totalAmountOverdue != null
                ? String.format(java.util.Locale.US, "%.2f", statistics.totalAmountOverdue / 100.0) : null;
    }
    public int getPaidCount() { return statistics != null ? statistics.paidCount : 0; }
    public int getPendingCount() { return statistics != null ? statistics.pendingCount : 0; }
    public int getOverdueCount() { return statistics != null ? statistics.overdueCount : 0; }
    public String getCurrency() { return "USD"; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Statistics {
        private int paidCount;
        private int pendingCount;
        private int overdueCount;
        @JsonProperty("totalAmountPaid")
        private Long totalAmountPaid;
        @JsonProperty("totalAmountPending")
        private Long totalAmountPending;
        @JsonProperty("totalAmountOverdue")
        private Long totalAmountOverdue;

        public int getPaidCount() { return paidCount; }
        public void setPaidCount(int paidCount) { this.paidCount = paidCount; }
        public int getPendingCount() { return pendingCount; }
        public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }
        public int getOverdueCount() { return overdueCount; }
        public void setOverdueCount(int overdueCount) { this.overdueCount = overdueCount; }
        public Long getTotalAmountPaid() { return totalAmountPaid; }
        public void setTotalAmountPaid(Long v) { this.totalAmountPaid = v; }
        public Long getTotalAmountPending() { return totalAmountPending; }
        public void setTotalAmountPending(Long v) { this.totalAmountPending = v; }
        public Long getTotalAmountOverdue() { return totalAmountOverdue; }
        public void setTotalAmountOverdue(Long v) { this.totalAmountOverdue = v; }
    }
}
