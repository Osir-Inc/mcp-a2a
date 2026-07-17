package com.osir.a2a.protocol;

import java.util.List;
import java.util.Map;

/**
 * A2A Agent Card - published at /.well-known/agent.json for agent discovery.
 * Follows the Google A2A Agent Card specification.
 */
public class AgentCard {

    private String name;
    private String description;
    private String url;
    private String version;
    private String documentationUrl;
    private AgentProvider provider;
    private AgentCapabilities capabilities;
    private AgentAuthentication authentication;
    private List<Skill> skills;

    public AgentCard() {}

    // Getters and setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDocumentationUrl() { return documentationUrl; }
    public void setDocumentationUrl(String documentationUrl) { this.documentationUrl = documentationUrl; }
    public AgentProvider getProvider() { return provider; }
    public void setProvider(AgentProvider provider) { this.provider = provider; }
    public AgentCapabilities getCapabilities() { return capabilities; }
    public void setCapabilities(AgentCapabilities capabilities) { this.capabilities = capabilities; }
    public AgentAuthentication getAuthentication() { return authentication; }
    public void setAuthentication(AgentAuthentication authentication) { this.authentication = authentication; }
    public List<Skill> getSkills() { return skills; }
    public void setSkills(List<Skill> skills) { this.skills = skills; }

    public static class AgentProvider {
        private String organization;
        private String url;

        public AgentProvider() {}
        public AgentProvider(String organization, String url) {
            this.organization = organization;
            this.url = url;
        }

        public String getOrganization() { return organization; }
        public void setOrganization(String organization) { this.organization = organization; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }

    public static class AgentCapabilities {
        private boolean streaming;
        private boolean pushNotifications;

        public AgentCapabilities() {}
        public AgentCapabilities(boolean streaming, boolean pushNotifications) {
            this.streaming = streaming;
            this.pushNotifications = pushNotifications;
        }

        public boolean isStreaming() { return streaming; }
        public void setStreaming(boolean streaming) { this.streaming = streaming; }
        public boolean isPushNotifications() { return pushNotifications; }
        public void setPushNotifications(boolean pushNotifications) { this.pushNotifications = pushNotifications; }
    }

    public static class AgentAuthentication {
        private List<String> schemes;

        public AgentAuthentication() {}
        public AgentAuthentication(List<String> schemes) {
            this.schemes = schemes;
        }

        public List<String> getSchemes() { return schemes; }
        public void setSchemes(List<String> schemes) { this.schemes = schemes; }
    }
}
