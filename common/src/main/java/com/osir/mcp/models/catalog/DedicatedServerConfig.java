package com.osir.mcp.models.catalog;

public class DedicatedServerConfig {
    private String id;
    private String name;
    private String description;
    private String cpu;
    private String memory;
    private String storage;
    private String bandwidth;
    private String price;
    private String currency;
    private String location;
    private boolean available;

    public DedicatedServerConfig() {}

    public DedicatedServerConfig(String id, String name, String description, String cpu, String memory,
                                  String storage, String bandwidth, String price, String currency,
                                  String location, boolean available) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cpu = cpu;
        this.memory = memory;
        this.storage = storage;
        this.bandwidth = bandwidth;
        this.price = price;
        this.currency = currency;
        this.location = location;
        this.available = available;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCpu() { return cpu; }
    public void setCpu(String cpu) { this.cpu = cpu; }

    public String getMemory() { return memory; }
    public void setMemory(String memory) { this.memory = memory; }

    public String getStorage() { return storage; }
    public void setStorage(String storage) { this.storage = storage; }

    public String getBandwidth() { return bandwidth; }
    public void setBandwidth(String bandwidth) { this.bandwidth = bandwidth; }

    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
