package com.osir.mcp.models.transfer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// API shape: { transfers: [PendingTransfer], total }
@JsonIgnoreProperties(ignoreUnknown = true)
public class PendingTransferListApiResponse {
    private List<PendingTransfer> transfers;
    private int total;

    public List<PendingTransfer> getTransfers() { return transfers; }
    public void setTransfers(List<PendingTransfer> transfers) { this.transfers = transfers; }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
}
