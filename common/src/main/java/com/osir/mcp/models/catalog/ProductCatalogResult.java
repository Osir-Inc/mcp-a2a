package com.osir.mcp.models.catalog;

public class ProductCatalogResult {
    private boolean success;
    private String message;
    private ProductCatalogResponse catalog;

    public ProductCatalogResult() {}

    public ProductCatalogResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public ProductCatalogResult(boolean success, String message, ProductCatalogResponse catalog) {
        this.success = success;
        this.message = message;
        this.catalog = catalog;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public ProductCatalogResponse getCatalog() { return catalog; }
    public void setCatalog(ProductCatalogResponse catalog) { this.catalog = catalog; }
}
