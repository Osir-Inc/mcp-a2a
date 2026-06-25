package com.osir.a2a.protocol;

import java.util.Map;

public class DataPart extends Part {

    private Map<String, Object> data;

    public DataPart() {}

    public DataPart(Map<String, Object> data) {
        this.data = data;
    }

    @Override
    public String getType() { return "data"; }

    public Map<String, Object> getData() { return data; }
    public void setData(Map<String, Object> data) { this.data = data; }
}
