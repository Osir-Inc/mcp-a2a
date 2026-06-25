package com.osir.mcp.services;

import com.osir.mcp.clients.CatalogBackendClient;
import com.osir.mcp.models.catalog.*;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class CatalogService {

    @Inject
    @RestClient
    CatalogBackendClient backendClient;

    @CacheResult(cacheName = "product-catalog")
    public ProductCatalogResult getProductCatalog() {
        try {
            ProductCatalogResponse response = backendClient.getProductCatalog();
            return new ProductCatalogResult(true, "Product catalog retrieved successfully", response);
        } catch (Exception e) {
            return new ProductCatalogResult(false, "Failed to retrieve product catalog: " + e.getMessage());
        }
    }

    @CacheResult(cacheName = "domain-extensions")
    public DomainExtensionsResult getDomainExtensions() {
        try {
            var response = backendClient.getDomainExtensions();
            return new DomainExtensionsResult(true, "Domain extensions retrieved successfully", response.getExtensions());
        } catch (Exception e) {
            return new DomainExtensionsResult(false, "Failed to retrieve domain extensions: " + e.getMessage());
        }
    }

    @CacheResult(cacheName = "dedicated-catalog")
    public DedicatedServerCatalogResult getDedicatedServerCatalog() {
        try {
            List<DedicatedServerConfig> servers = backendClient.getDedicatedServerCatalog();
            return new DedicatedServerCatalogResult(true, "Dedicated server catalog retrieved successfully", servers);
        } catch (Exception e) {
            return new DedicatedServerCatalogResult(false, "Failed to retrieve dedicated server catalog: " + e.getMessage());
        }
    }
}
