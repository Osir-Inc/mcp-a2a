package com.osir.mcp.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class DomainInfoResponse {
    private String name;
    private String roid;
    private List<DomainStatus> status;
    private String upDate;
    private String exDate;
    private String crDate;
    private NS ns;
    private int registrant;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoid() {
        return roid;
    }

    public void setRoid(String roid) {
        this.roid = roid;
    }

    public List<DomainStatus> getStatus() {
        return status;
    }

    public void setStatus(List<DomainStatus> status) {
        this.status = status;
    }

    public String getUpDate() {
        return upDate;
    }

    public void setUpDate(String upDate) {
        this.upDate = upDate;
    }

    public String getExDate() {
        return exDate;
    }

    public void setExDate(String exDate) {
        this.exDate = exDate;
    }

    public String getCrDate() {
        return crDate;
    }

    public void setCrDate(String crDate) {
        this.crDate = crDate;
    }

    public NS getNs() {
        return ns;
    }

    public void setNs(NS ns) {
        this.ns = ns;
    }

    public int getRegistrant() {
        return registrant;
    }

    public void setRegistrant(int registrant) {
        this.registrant = registrant;
    }
}
class NS {
    private List<String> hostObjs;

    public List<String> getHostObjs() {
        return hostObjs;
    }

    public void setHostObjs(List<String> hostObjs) {
        this.hostObjs = hostObjs;
    }
}

class DomainStatus {
    @JsonProperty("s")
    private String s;

    public DomainStatus() {}

    public DomainStatus(String s) {
        this.s = s;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }

    @Override
    public String toString() {
        return s;
    }
}