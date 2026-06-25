package com.osir.a2a.protocol;

import java.util.Map;

/**
 * JSON-RPC 2.0 request envelope for A2A protocol.
 * Methods: tasks/send, tasks/get, tasks/cancel
 */
public class JsonRpcRequest {

    private String jsonrpc = "2.0";
    private String method;
    private String id;
    private Map<String, Object> params;

    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
