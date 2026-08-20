package com.osir.mcp.models.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One DNS record a mail domain needs (MX, SPF, DKIM, ...), for external-DNS customers. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MailDnsRecordInfo {
    private String name;
    private String type;
    private String value;

    public MailDnsRecordInfo() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
