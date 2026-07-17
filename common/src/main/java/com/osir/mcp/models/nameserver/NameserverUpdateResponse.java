package com.osir.mcp.models.nameserver;

import java.util.List;

public class NameserverUpdateResponse {
    private boolean success;
    private String error;
    private String errorCode;
    private String timestamp;
    private NameserverUpdateData data;

    public static class NameserverUpdateData {
        private String domain;
        private boolean success;
        private String message;
        private List<String> previousNameservers;
        private List<String> newNameservers;
        private String registryTransactionId;
        private String clientTransactionId;
        private String updateTimestamp;
        private String status;
        private String tenantId;
        private String environment;
        private String registrar;
        private String customerId;

        // Getters and Setters
        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public List<String> getPreviousNameservers() { return previousNameservers; }
        public void setPreviousNameservers(List<String> previousNameservers) { this.previousNameservers = previousNameservers; }

        public List<String> getNewNameservers() { return newNameservers; }
        public void setNewNameservers(List<String> newNameservers) { this.newNameservers = newNameservers; }

        public String getRegistryTransactionId() { return registryTransactionId; }
        public void setRegistryTransactionId(String registryTransactionId) { this.registryTransactionId = registryTransactionId; }

        public String getClientTransactionId() { return clientTransactionId; }
        public void setClientTransactionId(String clientTransactionId) { this.clientTransactionId = clientTransactionId; }

        public String getUpdateTimestamp() { return updateTimestamp; }
        public void setUpdateTimestamp(String updateTimestamp) { this.updateTimestamp = updateTimestamp; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getTenantId() { return tenantId; }
        public void setTenantId(String tenantId) { this.tenantId = tenantId; }

        public String getEnvironment() { return environment; }
        public void setEnvironment(String environment) { this.environment = environment; }

        public String getRegistrar() { return registrar; }
        public void setRegistrar(String registrar) { this.registrar = registrar; }

        public String getCustomerId() { return customerId; }
        public void setCustomerId(String customerId) { this.customerId = customerId; }
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public NameserverUpdateData getData() { return data; }
    public void setData(NameserverUpdateData data) { this.data = data; }
}
