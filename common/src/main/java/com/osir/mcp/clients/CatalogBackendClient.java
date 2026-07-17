package com.osir.mcp.clients;

import com.osir.mcp.models.catalog.DedicatedServerConfig;
import com.osir.mcp.models.catalog.DomainExtension;
import com.osir.mcp.models.catalog.DomainExtensionsApiResponse;
import com.osir.mcp.models.catalog.ProductCatalogResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "domain-backend")
@RegisterProvider(UserAgentClientFilter.class)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface CatalogBackendClient {

    @GET
    @Path("/v1/public/catalog")
    ProductCatalogResponse getProductCatalog();

    @GET
    @Path("/v1/public/catalog/domains")
    DomainExtensionsApiResponse getDomainExtensions();

    @GET
    @Path("/v1/public/catalog/dedicated")
    List<DedicatedServerConfig> getDedicatedServerCatalog();
}
