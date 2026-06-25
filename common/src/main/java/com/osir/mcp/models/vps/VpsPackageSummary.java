package com.osir.mcp.models.vps;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VpsPackageSummary {
    private String id;
    private String name;
    private String description;
    private int cpuCores;
    private int memoryMb;
    private int storageGb;
    private int trafficGb;
    private String storageProfile;
    private long priceMonthly;
    private long priceSemiAnnual;
    private long priceAnnual;
    private long priceBiennial;
    private long priceTriennial;
    private VpsLocation location;
    private String countryCode;
    private String status;

    public VpsPackageSummary() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getCpuCores() { return cpuCores; }
    public void setCpuCores(int cpuCores) { this.cpuCores = cpuCores; }

    public int getMemoryMb() { return memoryMb; }
    public void setMemoryMb(int memoryMb) { this.memoryMb = memoryMb; }

    public int getStorageGb() { return storageGb; }
    public void setStorageGb(int storageGb) { this.storageGb = storageGb; }

    public int getTrafficGb() { return trafficGb; }
    public void setTrafficGb(int trafficGb) { this.trafficGb = trafficGb; }

    public String getStorageProfile() { return storageProfile; }
    public void setStorageProfile(String storageProfile) { this.storageProfile = storageProfile; }

    public long getPriceMonthly() { return priceMonthly; }
    public void setPriceMonthly(long priceMonthly) { this.priceMonthly = priceMonthly; }

    public long getPriceSemiAnnual() { return priceSemiAnnual; }
    public void setPriceSemiAnnual(long priceSemiAnnual) { this.priceSemiAnnual = priceSemiAnnual; }

    public long getPriceAnnual() { return priceAnnual; }
    public void setPriceAnnual(long priceAnnual) { this.priceAnnual = priceAnnual; }

    public long getPriceBiennial() { return priceBiennial; }
    public void setPriceBiennial(long priceBiennial) { this.priceBiennial = priceBiennial; }

    public long getPriceTriennial() { return priceTriennial; }
    public void setPriceTriennial(long priceTriennial) { this.priceTriennial = priceTriennial; }

    public VpsLocation getLocation() { return location; }
    public void setLocation(VpsLocation location) { this.location = location; }

    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String countryCode) { this.countryCode = countryCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
