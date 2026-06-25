package com.osir.mcp;

import com.osir.mcp.models.catalog.*;
import com.osir.mcp.services.CatalogService;
import io.quarkiverse.mcp.server.McpConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogMCPServerTest {

    @Mock
    CatalogService catalogService;

    @Mock
    McpConnection mockConnection;

    @InjectMocks
    CatalogMCPServer mcpServer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockConnection.id()).thenReturn("test-conn");
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

    private static DomainExtension extWithMeta(String tld, int regCents, String extensionType,
                                                boolean hasRestrictions, boolean hasPremium,
                                                List<String> categories, List<String> audience) {
        DomainExtension e = new DomainExtension();
        e.setTld(tld);
        e.setRegistrationPriceCents(regCents);
        e.setExtensionType(extensionType);
        e.setHasRestrictions(hasRestrictions);
        e.setHasPremium(hasPremium);
        e.setCategories(categories);
        e.setAudience(audience);
        return e;
    }

    // ===== getProductCatalog =====

    @Test
    void getProductCatalog_delegatesToService() {
        ProductCatalogResponse catalog = new ProductCatalogResponse();
        ProductCatalogResult expected = new ProductCatalogResult(true, "Product catalog retrieved successfully", catalog);
        when(catalogService.getProductCatalog()).thenReturn(expected);

        ProductCatalogResult result = mcpServer.getProductCatalog(mockConnection);

        assertSame(expected, result);
        verify(catalogService).getProductCatalog();
    }

    @Test
    void getProductCatalog_handlesException() {
        when(catalogService.getProductCatalog()).thenThrow(new RuntimeException("Unexpected error"));

        ProductCatalogResult result = mcpServer.getProductCatalog(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Unexpected error"));
    }

    // ===== getDomainExtensions =====

    @Test
    void getDomainExtensions_delegatesToService() {
        List<DomainExtension> extensions = List.of(ext(".com", 1299, 1499, 1099, "verisign"));
        DomainExtensionsResult expected = new DomainExtensionsResult(true, "Domain extensions retrieved successfully", extensions);
        when(catalogService.getDomainExtensions()).thenReturn(expected);

        DomainExtensionsResult result = mcpServer.getDomainExtensions(mockConnection);

        assertSame(expected, result);
        verify(catalogService).getDomainExtensions();
    }

    @Test
    void getDomainExtensions_handlesException() {
        when(catalogService.getDomainExtensions()).thenThrow(new RuntimeException("Service down"));

        DomainExtensionsResult result = mcpServer.getDomainExtensions(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Service down"));
    }

    // ===== getDedicatedServerCatalog =====

    @Test
    void getDedicatedServerCatalog_delegatesToService() {
        List<DedicatedServerConfig> servers = List.of(
                new DedicatedServerConfig("ds-1", "Basic", "Entry-level",
                        "Xeon", "32GB", "1TB", "10TB", "99.00", "USD", "US-East", true)
        );
        DedicatedServerCatalogResult expected = new DedicatedServerCatalogResult(true, "Dedicated server catalog retrieved successfully", servers);
        when(catalogService.getDedicatedServerCatalog()).thenReturn(expected);

        DedicatedServerCatalogResult result = mcpServer.getDedicatedServerCatalog(mockConnection);

        assertSame(expected, result);
        verify(catalogService).getDedicatedServerCatalog();
    }

    @Test
    void getDedicatedServerCatalog_handlesException() {
        when(catalogService.getDedicatedServerCatalog()).thenThrow(new RuntimeException("Network error"));

        DedicatedServerCatalogResult result = mcpServer.getDedicatedServerCatalog(mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Network error"));
    }

    // ===== listCategorizedTlds =====

    @Test
    void listCategorizedTlds_noFilters_returnsAllCategorizedAlphabetical() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".tech", 1200, "gTLD", false, false, List.of("tech"), List.of("developer")),
                        extWithMeta(".bio",  5600, "gTLD", false, false, List.of("health"), List.of("professional")),
                        extWithMeta(".app",  1400, "gTLD", false, false, List.of("mobile"), List.of("b2c")),
                        ext(".xyz", 500, 500, 500, "centralnic")  // no categories/audience — excluded
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(3, result.getTotalCandidates());
        List<String> tlds = result.getCandidates().stream().map(CategorizedTldCandidate::getTld).toList();
        assertEquals(List.of("app", "bio", "tech"), tlds); // alphabetical
        assertTrue(tlds.stream().noneMatch("xyz"::equals));
    }

    @Test
    void listCategorizedTlds_excludeRestricted_dropsRestrictedTlds() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".health", 9900, "gTLD", true,  false, List.of("health"), List.of("b2c")),
                        extWithMeta(".app",    1400, "gTLD", false, false, List.of("mobile"), List.of("b2c"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(true, null, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("app", result.getCandidates().get(0).getTld());
        assertTrue(result.getFiltersApplied().isExcludeRestricted());
    }

    @Test
    void listCategorizedTlds_excludeCcTLDs_dropsCcAndIDN() {
        // Backend returns plural forms: "ccTLDs", "IDNs", "gTLDs"
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".de",      800,  "ccTLDs", false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".xn--p1ai", 900, "IDNs",   false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".io",      2000, "gTLDs",  false, false, List.of("tech"),    List.of("developer"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, true, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("io", result.getCandidates().get(0).getTld());
        assertTrue(result.getFiltersApplied().isExcludeCcTLDs());
    }

    @Test
    void listCategorizedTlds_excludeCcTLDs_handlesSingularAndPluralForms() {
        // Defensive: handle both "ccTLD" and "ccTLDs", "IDN" and "IDNs"
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".de",      800, "ccTLD",  false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".uk",      800, "ccTLDs", false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".xn--p1ai", 900, "IDN",  false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".xn--90a", 900, "IDNs",  false, false, List.of("generic"), List.of("b2c")),
                        extWithMeta(".io",      2000, "gTLDs", false, false, List.of("tech"),    List.of("developer"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, true, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("io", result.getCandidates().get(0).getTld());
    }

    @Test
    void listCategorizedTlds_maxRegisterPrice_dropsExpensiveTlds() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".ai",  8000, "gTLD", false, false, List.of("ai"),   List.of("developer")),
                        extWithMeta(".app", 1400, "gTLD", false, false, List.of("mobile"), List.of("b2c"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, 15.00, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("app", result.getCandidates().get(0).getTld());
        assertEquals(15.00, result.getFiltersApplied().getMaxRegisterPrice());
    }

    @Test
    void listCategorizedTlds_excludePremium_dropsPremiumTlds() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".ai",  8000, "gTLD", false, true,  List.of("ai"),   List.of("developer")),
                        extWithMeta(".app", 1400, "gTLD", false, false, List.of("mobile"), List.of("b2c"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, true, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("app", result.getCandidates().get(0).getTld());
    }

    @Test
    void listCategorizedTlds_registryFilter_returnsOnlyMatchingRegistry() {
        DomainExtension google = extWithMeta(".app", 1400, "gTLD", false, false, List.of("mobile"), List.of("b2c"));
        google.setRegistry("Google");
        DomainExtension verisign = extWithMeta(".com", 1299, "gTLD", false, false, List.of("generic"), List.of("b2c"));
        verisign.setRegistry("Verisign");
        when(catalogService.getDomainExtensions()).thenReturn(
                new DomainExtensionsResult(true, "ok", List.of(google, verisign)));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, "Google", mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("app", result.getCandidates().get(0).getTld());
        assertEquals("Google", result.getCandidates().get(0).getRegistryName());
        assertEquals("Google", result.getFiltersApplied().getRegistry());
    }

    @Test
    void listCategorizedTlds_noMatchingTlds_returnsSuccessWithHint() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(extWithMeta(".bio", 5600, "gTLD", false, false, List.of("health"), List.of("professional")))));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, 5.00, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalCandidates());
        assertTrue(result.getCandidates().isEmpty());
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("relaxing"));
    }

    @Test
    void listCategorizedTlds_untaggedTldsNeverSurface() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        ext(".xyz", 500, 500, 500, "centralnic"),                                   // no setters called
                        extWithMeta(".net", 1200, "gTLD", false, false, null, List.of("b2c")),      // null categories
                        extWithMeta(".biz", 1200, "gTLD", false, false, List.of("business"), null)  // null audience
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalCandidates());
    }

    @Test
    void listCategorizedTlds_emptyListMetadata_excluded() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        extWithMeta(".foo", 1000, "gTLD", false, false, List.of(), List.of("b2c")),    // empty categories
                        extWithMeta(".bar", 1000, "gTLD", false, false, List.of("tech"), List.of()),   // empty audience
                        extWithMeta(".baz", 1000, "gTLD", false, false, List.of(), List.of())          // both empty
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getTotalCandidates());
    }

    @Test
    void listCategorizedTlds_maxRegisterPrice_nullPricePassesThrough() {
        DomainExtension nullPrice = extWithMeta(".free", 1000, "gTLD", false, false,
                List.of("generic"), List.of("b2c"));
        nullPrice.setRegistrationPriceCents(null);
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(
                        nullPrice,
                        extWithMeta(".expensive", 50000, "gTLD", false, false, List.of("premium"), List.of("b2b"))
                )));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, 15.00, null, null, mockConnection);

        assertTrue(result.isSuccess());
        assertEquals(1, result.getTotalCandidates());
        assertEquals("free", result.getCandidates().get(0).getTld());
    }

    @Test
    void listCategorizedTlds_catalogUnavailable_returnsError() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(false, "Backend down"));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertFalse(result.isSuccess());
    }

    @Test
    void listCategorizedTlds_catalogThrows_returnsError() {
        when(catalogService.getDomainExtensions()).thenThrow(new RuntimeException("Network error"));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("Network error"));
    }

    @Test
    void listCategorizedTlds_tldNormalized_leadingDotStripped() {
        when(catalogService.getDomainExtensions()).thenReturn(new DomainExtensionsResult(true, "ok",
                List.of(extWithMeta(".tech", 1200, "gTLD", false, false, List.of("tech"), List.of("developer")))));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(null, null, null, null, null, mockConnection);

        assertEquals("tech", result.getCandidates().get(0).getTld());
    }

    @Test
    void listCategorizedTlds_filtersEchoedCorrectly() {
        when(catalogService.getDomainExtensions()).thenReturn(
                new DomainExtensionsResult(true, "ok", List.of()));

        CategorizedTldsResult result = mcpServer.listCategorizedTlds(true, true, 12.99, true, "GOOGLE", mockConnection);

        CategorizedTldsFilters f = result.getFiltersApplied();
        assertTrue(f.isExcludeRestricted());
        assertTrue(f.isExcludeCcTLDs());
        assertEquals(12.99, f.getMaxRegisterPrice());
        assertTrue(f.isExcludePremium());
        assertEquals("GOOGLE", f.getRegistry());
    }
}
