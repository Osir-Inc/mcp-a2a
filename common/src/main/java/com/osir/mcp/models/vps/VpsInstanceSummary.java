package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Maps a VPS instance from GET /v1/hosting/vps/instances.
 * packageName, cpuCores, memoryMb, storageGb are nested under vpsPackage.
 * location is derived from hypervisorGroup.city / countryName.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsInstanceSummary {
    private String id;
    private String hostname;
    private String status;
    private String ipAddress;
    private String paymentTerm;
    private String nextRenewalDate;
    private String createdAt;

    private VpsPackageInfo vpsPackage;
    private HypervisorGroupInfo hypervisorGroup;

    public VpsInstanceSummary() {}

    // Core fields
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getPaymentTerm() { return paymentTerm; }
    public void setPaymentTerm(String paymentTerm) { this.paymentTerm = paymentTerm; }

    public String getNextRenewalDate() { return nextRenewalDate; }
    public void setNextRenewalDate(String nextRenewalDate) { this.nextRenewalDate = nextRenewalDate; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // Nested objects
    public VpsPackageInfo getVpsPackage() { return vpsPackage; }
    public void setVpsPackage(VpsPackageInfo vpsPackage) { this.vpsPackage = vpsPackage; }

    public HypervisorGroupInfo getHypervisorGroup() { return hypervisorGroup; }
    public void setHypervisorGroup(HypervisorGroupInfo hypervisorGroup) { this.hypervisorGroup = hypervisorGroup; }

    // Convenience accessors — read from nested objects
    public String getPackageName() {
        return vpsPackage != null ? vpsPackage.getName() : null;
    }

    public int getCpuCores() {
        return vpsPackage != null && vpsPackage.getCpuCores() != null ? vpsPackage.getCpuCores() : 0;
    }

    public int getMemoryMb() {
        return vpsPackage != null && vpsPackage.getMemoryMb() != null ? vpsPackage.getMemoryMb() : 0;
    }

    public int getStorageGb() {
        return vpsPackage != null && vpsPackage.getStorageGb() != null ? vpsPackage.getStorageGb() : 0;
    }

    public String getLocation() {
        if (hypervisorGroup == null) return null;
        if (hypervisorGroup.getCity() != null) return hypervisorGroup.getCity();
        return hypervisorGroup.getCountryName();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VpsPackageInfo {
        private String name;
        private Integer cpuCores;
        private Integer memoryMb;
        private Integer storageGb;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public Integer getCpuCores() { return cpuCores; }
        public void setCpuCores(Integer cpuCores) { this.cpuCores = cpuCores; }

        public Integer getMemoryMb() { return memoryMb; }
        public void setMemoryMb(Integer memoryMb) { this.memoryMb = memoryMb; }

        public Integer getStorageGb() { return storageGb; }
        public void setStorageGb(Integer storageGb) { this.storageGb = storageGb; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HypervisorGroupInfo {
        private String city;
        private String countryName;
        private String countryCode;

        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }

        public String getCountryName() { return countryName; }
        public void setCountryName(String countryName) { this.countryName = countryName; }

        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
    }
}
