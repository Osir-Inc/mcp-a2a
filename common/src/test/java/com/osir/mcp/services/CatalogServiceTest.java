package com.osir.mcp.services;

import com.osir.mcp.clients.CatalogBackendClient;
import com.osir.mcp.models.catalog.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogServiceTest {

    @Mock
    CatalogBackendClient backendClient;

    @InjectMocks
    CatalogService catalogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static DomainExtension ext(String tld, int regCents, int renCents, int trCents, String registry) {
        DomainExtension e = new DomainExtension();
        e.setTld(tld);
        e.setRegistrationPriceCents(regCents);
        e.setRenewalPriceCents(renCents);
        e.setTransferPriceCents(trCents);
        e.setRegistry(registry);
        return e;
    }

    // ===== getProductCatalog tests =====

    @Test
    void getProductCatalog_success() {
        List<DomainExtension> extensions = List.of(
                ext(".com", 1299, 1499, 1099, "verisign")
        );
        DomainExtensionsApiResponse domainsWrapper = new DomainExtensionsApiResponse();
        domainsWrapper.setExtensions(extensions);
        List<DedicatedServerConfig> servers = List.of(
                new DedicatedServerConfig("ds-1", "Basic Dedicated", "Entry-level server",
                        "Intel Xeon E-2236", "32GB", "1TB SSD", "10TB",
                        "99.00", "USD", "US-East", true)
        );
        ProductCatalogResponse response = new ProductCatalogResponse();
        response.setDomains(domainsWrapper);
        response.setDedicatedServers(servers);
        when(backendClient.getProductCatalog()).thenReturn(response);

        ProductCatalogResult result = catalogService.getProductCatalog();

        assertTrue(result.isSuccess());
        assertEquals("Product catalog retrieved successfully", result.getMessage());
        assertNotNull(result.getCatalog());
        assertEquals(1, result.getCatalog().getDomains().getExtensions().size());
        assertEquals(1, result.getCatalog().getDedicatedServers().size());
        verify(backendClient).getProductCatalog();
    }

    @Test
    void getProductCatalog_backendError() {
        when(backendClient.getProductCatalog()).thenThrow(new RuntimeException("Connection refused"));

        ProductCatalogResult result = catalogService.getProductCatalog();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Connection refused"));
        assertNull(result.getCatalog());
    }

    // ===== getDomainExtensions tests =====

    @Test
    void getDomainExtensions_success() {
        List<DomainExtension> extensions = List.of(
                ext(".com", 1299, 1499, 1099, "verisign"),
                ext(".net", 1199, 1399, 999, "verisign")
        );
        var extResponse = new com.osir.mcp.models.catalog.DomainExtensionsApiResponse();
        extResponse.setExtensions(extensions);
        when(backendClient.getDomainExtensions()).thenReturn(extResponse);

        DomainExtensionsResult result = catalogService.getDomainExtensions();

        assertTrue(result.isSuccess());
        assertEquals("Domain extensions retrieved successfully", result.getMessage());
        assertNotNull(result.getExtensions());
        assertEquals(2, result.getExtensions().size());
        assertEquals(".com", result.getExtensions().get(0).getTld());
        assertEquals("12.99", result.getExtensions().get(0).getRegistrationPrice());
        assertEquals(".net", result.getExtensions().get(1).getTld());
        verify(backendClient).getDomainExtensions();
    }

    @Test
    void getDomainExtensions_backendError() {
        when(backendClient.getDomainExtensions()).thenThrow(new RuntimeException("Service unavailable"));

        DomainExtensionsResult result = catalogService.getDomainExtensions();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Service unavailable"));
        assertNull(result.getExtensions());
    }

    // ===== getDedicatedServerCatalog tests =====

    @Test
    void getDedicatedServerCatalog_success() {
        List<DedicatedServerConfig> servers = List.of(
                new DedicatedServerConfig("ds-1", "Basic Dedicated", "Entry-level server",
                        "Intel Xeon E-2236", "32GB", "1TB SSD", "10TB",
                        "99.00", "USD", "US-East", true),
                new DedicatedServerConfig("ds-2", "Pro Dedicated", "High-performance server",
                        "AMD EPYC 7543", "128GB", "4TB NVMe", "Unmetered",
                        "299.00", "USD", "EU-West", true)
        );
        when(backendClient.getDedicatedServerCatalog()).thenReturn(servers);

        DedicatedServerCatalogResult result = catalogService.getDedicatedServerCatalog();

        assertTrue(result.isSuccess());
        assertEquals("Dedicated server catalog retrieved successfully", result.getMessage());
        assertNotNull(result.getServers());
        assertEquals(2, result.getServers().size());
        assertEquals("ds-1", result.getServers().get(0).getId());
        assertEquals("ds-2", result.getServers().get(1).getId());
        verify(backendClient).getDedicatedServerCatalog();
    }

    @Test
    void getDedicatedServerCatalog_backendError() {
        when(backendClient.getDedicatedServerCatalog()).thenThrow(new RuntimeException("Timeout"));

        DedicatedServerCatalogResult result = catalogService.getDedicatedServerCatalog();

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Timeout"));
        assertNull(result.getServers());
    }
}
