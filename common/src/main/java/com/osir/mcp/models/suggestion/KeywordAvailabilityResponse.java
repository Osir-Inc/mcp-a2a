package com.osir.mcp.models.suggestion;

import java.util.List;

public class KeywordAvailabilityResponse {
    private String keyword;
    private Integer totalDomains;
    private Integer availableDomains;
    private Integer unavailableDomains;
    private Long processingTimeMs;
    private AvailabilityStats availabilityStats;
    private List<DomainAvailabilityResult> results;

    public static class AvailabilityStats {
        private Integer available;
        private Integer unavailable;
        private Integer unknown;

        public AvailabilityStats() {}

        public AvailabilityStats(Integer available, Integer unavailable, Integer unknown) {
            this.available = available;
            this.unavailable = unavailable;
            this.unknown = unknown;
        }

        // Getters and Setters
        public Integer getAvailable() { return available; }
        public void setAvailable(Integer available) { this.available = available; }

        public Integer getUnavailable() { return unavailable; }
        public void setUnavailable(Integer unavailable) { this.unavailable = unavailable; }

        public Integer getUnknown() { return unknown; }
        public void setUnknown(Integer unknown) { this.unknown = unknown; }
    }

    public static class DomainAvailabilityResult {
        private String domain;
        private String tld;
        private String registry;
        private String availability;
        private String reason;
        private Boolean premium;
        private Double price;

        public DomainAvailabilityResult() {}

        public DomainAvailabilityResult(String domain, String tld, String registry, String availability, 
                                       String reason, Boolean premium, Double price) {
            this.domain = domain;
            this.tld = tld;
            this.registry = registry;
            this.availability = availability;
            this.reason = reason;
            this.premium = premium;
            this.price = price;
        }

        // Getters and Setters
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public String getTld() { return tld; }
        public void setTld(String tld) { this.tld = tld; }

        public String getRegistry() { return registry; }
        public void setRegistry(String registry) { this.registry = registry; }

        public String getAvailability() { return availability; }
        public void setAvailability(String availability) { this.availability = availability; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Boolean getPremium() { return premium; }
        public void setPremium(Boolean premium) { this.premium = premium; }

        public Double getPrice() { return price; }
        public void setPrice(Double price) { this.price = price; }
    }

    public KeywordAvailabilityResponse() {}

    public KeywordAvailabilityResponse(String keyword, Integer totalDomains, Integer availableDomains, 
                                     Integer unavailableDomains, Long processingTimeMs, 
                                     AvailabilityStats availabilityStats, List<DomainAvailabilityResult> results) {
        this.keyword = keyword;
        this.totalDomains = totalDomains;
        this.availableDomains = availableDomains;
        this.unavailableDomains = unavailableDomains;
        this.processingTimeMs = processingTimeMs;
        this.availabilityStats = availabilityStats;
        this.results = results;
    }

    // Getters and Setters
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public Integer getTotalDomains() { return totalDomains; }
    public void setTotalDomains(Integer totalDomains) { this.totalDomains = totalDomains; }

    public Integer getAvailableDomains() { return availableDomains; }
    public void setAvailableDomains(Integer availableDomains) { this.availableDomains = availableDomains; }

    public Integer getUnavailableDomains() { return unavailableDomains; }
    public void setUnavailableDomains(Integer unavailableDomains) { this.unavailableDomains = unavailableDomains; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }

    public AvailabilityStats getAvailabilityStats() { return availabilityStats; }
    public void setAvailabilityStats(AvailabilityStats availabilityStats) { this.availabilityStats = availabilityStats; }

    public List<DomainAvailabilityResult> getResults() { return results; }
    public void setResults(List<DomainAvailabilityResult> results) { this.results = results; }
}