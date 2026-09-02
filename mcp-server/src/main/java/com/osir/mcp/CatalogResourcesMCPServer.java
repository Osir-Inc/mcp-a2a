package com.osir.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.services.CatalogService;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextResourceContents;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Read-mostly reference data exposed as MCP resources, so a client can attach it to context
 * once instead of the model calling the equivalent catalog tools repeatedly. Anonymous:
 * both are public catalog data (the underlying service responses are cached ~15 minutes).
 */
@ApplicationScoped
public class CatalogResourcesMCPServer {

    @Inject
    CatalogService catalogService;

    @Inject
    ObjectMapper objectMapper;

    @Resource(uri = "osir://catalog/tlds",
            name = "tld-catalog",
            title = "TLD catalog with pricing",
            description = "All domain extensions (TLDs) OSIR sells, with registration, renewal and transfer list prices. Public data, refreshed roughly every 15 minutes.",
            mimeType = "application/json")
    public TextResourceContents tldCatalog() throws Exception {
        return TextResourceContents.create("osir://catalog/tlds",
                objectMapper.writeValueAsString(catalogService.getDomainExtensions()));
    }

    @Resource(uri = "osir://catalog/products",
            name = "product-catalog",
            title = "Product catalog",
            description = "The full OSIR product catalog: VPS packages, dedicated server configurations, and other hosting products with list prices. Public data, refreshed roughly every 15 minutes.",
            mimeType = "application/json")
    public TextResourceContents productCatalog() throws Exception {
        return TextResourceContents.create("osir://catalog/products",
                objectMapper.writeValueAsString(catalogService.getProductCatalog()));
    }
}
