package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Maps GET /v2/domains — EPPApiResponse<DomainSearchResponse>.
 * Wire format: { success, data: { domains: [...], page, size, totalElements } }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainListResponse {

    private boolean success;
    private DomainSearchResponse data;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public DomainSearchResponse getData() { return data; }
    public void setData(DomainSearchResponse data) { this.data = data; }

    // Convenience accessors used by DomainService
    public List<DomainSummary> getDomains() { return data != null ? data.getDomains() : null; }
    public int getPage()      { return data != null ? data.getPage() : 0; }
    public int getPageSize()  { return data != null ? data.getSize() : 0; }
    public long getTotal()    { return data != null ? data.getTotalElements() : 0; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainSearchResponse {
        private List<DomainSummary> domains;
        private int page;
        private int size;
        private long totalElements;

        public List<DomainSummary> getDomains() { return domains; }
        public void setDomains(List<DomainSummary> domains) { this.domains = domains; }

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }

        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }

        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
    }
}
