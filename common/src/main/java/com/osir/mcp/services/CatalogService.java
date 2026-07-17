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

    // Self-injection so calls to the @CacheResult methods go through the CDI proxy
    // (interceptors don't fire on same-instance self-invocation). Falls back to `this`
    // in plain unit tests where the bean isn't CDI-managed (self is null, caching off).
    @Inject
    CatalogService self;

    private CatalogService cached() {
        return self != null ? self : this;
    }

    public ProductCatalogResult getProductCatalog() {
        try {
            return new ProductCatalogResult(true, "Product catalog retrieved successfully", cached().fetchProductCatalog());
        } catch (Exception e) {
            return new ProductCatalogResult(false, "Failed to retrieve product catalog: " + e.getMessage());
        }
    }

    public DomainExtensionsResult getDomainExtensions() {
        try {
            return new DomainExtensionsResult(true, "Domain extensions retrieved successfully",
                    cached().fetchDomainExtensions().getExtensions());
        } catch (Exception e) {
            return new DomainExtensionsResult(false, "Failed to retrieve domain extensions: " + e.getMessage());
        }
    }

    public DedicatedServerCatalogResult getDedicatedServerCatalog() {
        try {
            return new DedicatedServerCatalogResult(true, "Dedicated server catalog retrieved successfully",
                    cached().fetchDedicatedServerCatalog());
        } catch (Exception e) {
            return new DedicatedServerCatalogResult(false, "Failed to retrieve dedicated server catalog: " + e.getMessage());
        }
    }

    // Only successful backend responses are cached; failures throw and are not cached.

    @CacheResult(cacheName = "product-catalog")
    public ProductCatalogResponse fetchProductCatalog() {
        return backendClient.getProductCatalog();
    }

    @CacheResult(cacheName = "domain-extensions")
    public DomainExtensionsApiResponse fetchDomainExtensions() {
        return backendClient.getDomainExtensions();
    }

    @CacheResult(cacheName = "dedicated-catalog")
    public List<DedicatedServerConfig> fetchDedicatedServerCatalog() {
        return backendClient.getDedicatedServerCatalog();
    }
}
