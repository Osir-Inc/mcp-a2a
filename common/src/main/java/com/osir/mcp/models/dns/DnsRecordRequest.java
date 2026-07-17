package com.osir.mcp.models.dns;

public class DnsRecordRequest {
    private String name;
    private String type;
    private String content;
    private Integer ttl;
    private Integer priority;

    public DnsRecordRequest() {}

    public DnsRecordRequest(String name, String type, String content, Integer ttl, Integer priority) {
        this.name = name;
        this.type = type;
        this.content = content;
        this.ttl = ttl;
        this.priority = priority;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getTtl() { return ttl; }
    public void setTtl(Integer ttl) { this.ttl = ttl; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
