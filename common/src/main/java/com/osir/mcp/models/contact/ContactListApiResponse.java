package com.osir.mcp.models.contact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

// API shape: { page, pageSize, total, data: [ContactDetail] }
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContactListApiResponse {
    private int page;
    private int pageSize;
    private long total;
    private List<ContactDetail> data;

    public List<ContactDetail> getData() { return data; }
    public void setData(List<ContactDetail> data) { this.data = data; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
}
