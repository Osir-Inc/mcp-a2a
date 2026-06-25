package com.osir.a2a.protocol;

/**
 * JSON-RPC 2.0 response envelope for A2A protocol.
 */
public class JsonRpcResponse {

    private String jsonrpc = "2.0";
    private String id;
    private Object result;
    private JsonRpcError error;

    public static JsonRpcResponse success(String id, Object result) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.result = result;
        return response;
    }

    public static JsonRpcResponse error(String id, int code, String message) {
        JsonRpcResponse response = new JsonRpcResponse();
        response.id = id;
        response.error = new JsonRpcError(code, message);
        return response;
    }

    public String getJsonrpc() { return jsonrpc; }
    public void setJsonrpc(String jsonrpc) { this.jsonrpc = jsonrpc; }
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Object getResult() { return result; }
    public void setResult(Object result) { this.result = result; }
    public JsonRpcError getError() { return error; }
    public void setError(JsonRpcError error) { this.error = error; }

    public static class JsonRpcError {
        private int code;
        private String message;

        public JsonRpcError() {}
        public JsonRpcError(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    // Standard JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    // A2A-specific error codes
    public static final int TASK_NOT_FOUND = -32001;
    public static final int TASK_NOT_CANCELABLE = -32002;
    public static final int TOKEN_EXPIRED = -32003;
}
