package com.osir.mcp.models.transfer;

import java.util.List;

public class PendingTransferListResult {
    private boolean success;
    private String message;
    private List<PendingTransfer> transfers;

    public PendingTransferListResult() {}

    public PendingTransferListResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<PendingTransfer> getTransfers() { return transfers; }
    public void setTransfers(List<PendingTransfer> transfers) { this.transfers = transfers; }
}
