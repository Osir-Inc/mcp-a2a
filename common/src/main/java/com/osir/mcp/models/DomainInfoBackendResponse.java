package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainInfoBackendResponse {
    private String domain;
    private String environment;
    private String tenant;
    private String registrar;
    private String timestamp;
    private boolean success;
    private String status;
    private String message;
    private DomainData data;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainData {
        private String name;
        private String roid;
        // "status" is a single string (e.g. "active"); "statuses" is the EPP array
        private String status;
        private List<String> statuses;
        private String crDate;
        private String upDate;
        private String exDate;
        private List<String> nameservers;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getRoid() { return roid; }
        public void setRoid(String roid) { this.roid = roid; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<String> getStatuses() { return statuses; }
        public void setStatuses(List<String> statuses) { this.statuses = statuses; }

        public String getCrDate() { return crDate; }
        public void setCrDate(String crDate) { this.crDate = crDate; }

        public String getUpDate() { return upDate; }
        public void setUpDate(String upDate) { this.upDate = upDate; }

        public String getExDate() { return exDate; }
        public void setExDate(String exDate) { this.exDate = exDate; }

        public List<String> getNameservers() { return nameservers; }
        public void setNameservers(List<String> nameservers) { this.nameservers = nameservers; }
    }

    // Getters and setters
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    
    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    
    public String getRegistrar() { return registrar; }
    public void setRegistrar(String registrar) { this.registrar = registrar; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    
    public DomainData getData() { return data; }
    public void setData(DomainData data) { this.data = data; }
}