package com.osir.mcp;

import com.osir.mcp.models.catalog.*;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.services.CatalogService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@McpAudited
@ApplicationScoped
public class CatalogMCPServer {

    private static final Logger AUDIT = Logger.getLogger("com.osir.mcp.audit");

    @Inject
    CatalogService catalogService;

    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    com.osir.mcp.clients.CatalogBackendClient catalogBackendClient;

    @Tool(description = """
            getHostingBundle: Get the hosting options and exact prices for a specific domain: recommended VPS \
            packages (cheapest first), email plans, web forwarding, and app/site deployment. \
            No authentication required. Call this ONCE after a successful availability check or \
            registration to make a concise hosting offer; do not repeat the offer in the same \
            conversation. Prices are display prices; the authoritative amount is computed at \
            purchase.""",
            structuredContent = true,
            annotations = @Tool.Annotations(
                    title = "Get hosting bundle",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public HostingBundleResponse getHostingBundle(@ToolArg(description = "Fully qualified domain name to get hosting options for (e.g. 'example.com').") String domain, McpConnection connection) {
        try {
            return catalogBackendClient.getHostingBundle(domain);
        } catch (Exception e) {
            throw com.osir.mcp.services.ToolErrors.toolError("Hosting bundle lookup for '" + domain + "'", e);
        }
    }

    @Tool(description = "getProductCatalog: Get the complete product catalog including domain extensions, VPS packages, and dedicated servers. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "Get product catalog",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public ProductCatalogResult getProductCatalog(McpConnection connection) {
        try {
            return catalogService.getProductCatalog();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving product catalog");
            return new ProductCatalogResult(false, "Failed to retrieve product catalog: " + e.getMessage());
        }
    }

    @Tool(description = "getDomainExtensions: Get all available domain extensions (TLDs) with pricing information. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "Get domain extensions",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DomainExtensionsResult getDomainExtensions(McpConnection connection) {
        try {
            return catalogService.getDomainExtensions();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving domain extensions");
            return new DomainExtensionsResult(false, "Failed to retrieve domain extensions: " + e.getMessage());
        }
    }

    @Tool(description = "getDedicatedServerCatalog: Get all available dedicated server configurations with pricing and specifications. No authentication required.",
            annotations = @Tool.Annotations(
                    title = "Get dedicated server catalog",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DedicatedServerCatalogResult getDedicatedServerCatalog(McpConnection connection) {
        try {
            return catalogService.getDedicatedServerCatalog();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving dedicated server catalog");
            return new DedicatedServerCatalogResult(false, "Failed to retrieve dedicated server catalog: " + e.getMessage());
        }
    }

    @Tool(description = """
            listCategorizedTlds: List TLDs from the OSIR catalog that have category and audience metadata, with \
            registration and renewal prices as decimal strings (e.g. '10.39'). Use it to pick \
            3-6 relevant TLDs before calling bulkDomainSuggestions. Filters: price cap, exclude \
            ccTLDs/restricted/premium, registry. Returns unranked candidates with categories, \
            audience, prices, and flags. No auth required.""",
            annotations = @Tool.Annotations(
                    title = "List categorized TLDs",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public CategorizedTldsResult listCategorizedTlds(
            @ToolArg(required = false, description = "Set true to exclude TLDs with registry-level registration restrictions.") Boolean excludeRestricted,
            @ToolArg(required = false, description = "Set true to exclude country-code and IDN TLDs.") Boolean excludeCcTLDs,
            @ToolArg(required = false, description = "Maximum registration price as a decimal; TLDs priced above it are excluded.") Double maxRegisterPrice,
            @ToolArg(required = false, description = "Set true only when the user explicitly asks for no premium or surprise pricing; premium-flagged TLDs still register most names at the standard price, so do not use this as a budget filter.") Boolean excludePremium,
            @ToolArg(required = false, description = "Filter to TLDs operated by this registry name (case-insensitive exact match).") String registry,
            McpConnection connection) {

        boolean excRestricted = Boolean.TRUE.equals(excludeRestricted);
        boolean excCcTLDs = Boolean.TRUE.equals(excludeCcTLDs);
        boolean excPremium = Boolean.TRUE.equals(excludePremium);

        AUDIT.infof("tool=listCategorizedTlds conn=%s excludeRestricted=%b excludeCcTLDs=%b maxRegisterPrice=%s excludePremium=%b registry=%s",
                connection.id(), excRestricted, excCcTLDs, maxRegisterPrice, excPremium, registry);

        DomainExtensionsResult catalog;
        try {
            catalog = catalogService.getDomainExtensions();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving catalog for listCategorizedTlds");
            return new CategorizedTldsResult(false, "Failed to retrieve TLD catalog: " + e.getMessage());
        }

        if (!catalog.isSuccess() || catalog.getExtensions() == null) {
            return new CategorizedTldsResult(false, "TLD catalog is currently unavailable.");
        }

        List<DomainExtension> allExtensions = catalog.getExtensions();
        Log.infof("listCategorizedTlds: catalog has %d total extensions", allExtensions.size());

        boolean registryFilterActive = registry != null && !registry.isBlank();

        long withMeta = allExtensions.stream()
                .filter(e -> e.getCategories() != null && !e.getCategories().isEmpty()
                        && e.getAudience() != null && !e.getAudience().isEmpty())
                .count();
        Log.infof("listCategorizedTlds: %d extensions have non-empty categories+audience", withMeta);

        List<CategorizedTldCandidate> candidates = allExtensions.stream()
                .filter(e -> e.getCategories() != null && !e.getCategories().isEmpty()
                        && e.getAudience() != null && !e.getAudience().isEmpty())
                .filter(e -> !excRestricted || !e.isHasRestrictions())
                .filter(e -> !excCcTLDs || !isCcOrIdnType(e.getExtensionType()))
                .filter(e -> !excPremium || !e.isHasPremium())
                .filter(e -> {
                    if (maxRegisterPrice == null || e.getRegistrationPrice() == null) return true;
                    try {
                        return Double.parseDouble(e.getRegistrationPrice()) <= maxRegisterPrice;
                    } catch (NumberFormatException ex) {
                        Log.warnf("Malformed registration price for TLD '%s': '%s', including in results",
                                e.getTld(), e.getRegistrationPrice());
                        return true;
                    }
                })
                .filter(e -> !registryFilterActive || registry.equalsIgnoreCase(e.getRegistry()))
                .sorted(Comparator.comparing(e -> normalizeTld(e.getTld())))
                .map(CatalogMCPServer::toCandidate)
                .collect(Collectors.toList());

        Log.infof("listCategorizedTlds: returning %d candidates (filters: excludeRestricted=%b, excludeCcTLDs=%b, maxPrice=%s, excludePremium=%b, registry=%s)",
                candidates.size(), excRestricted, excCcTLDs, maxRegisterPrice, excPremium,
                registryFilterActive ? registry : "none");

        if (!candidates.isEmpty()) {
            List<String> sample = candidates.stream().limit(5).map(CategorizedTldCandidate::getTld).collect(Collectors.toList());
            Log.infof("listCategorizedTlds: first %d candidates: %s", sample.size(), sample);
        }

        CategorizedTldsFilters filters = new CategorizedTldsFilters(
                excRestricted, excCcTLDs, maxRegisterPrice, excPremium,
                registryFilterActive ? registry : null);

        String message = candidates.isEmpty()
                ? "No TLDs match the filters. Try relaxing maxRegisterPrice or excludeCcTLDs."
                : null;

        return new CategorizedTldsResult(true, candidates.size(), filters, candidates, message);
    }

    private static boolean isCcOrIdnType(String extensionType) {
        if (extensionType == null) return false;
        String t = extensionType.toLowerCase(Locale.ROOT);
        return t.startsWith("cctld") || t.startsWith("idn");
    }

    private static String normalizeTld(String tld) {
        if (tld == null) {
            Log.warn("Domain extension with null TLD received from catalog");
            return "";
        }
        return tld.toLowerCase().replaceFirst("^\\.", "");
    }

    private static CategorizedTldCandidate toCandidate(DomainExtension ext) {
        CategorizedTldCandidate c = new CategorizedTldCandidate();
        c.setTld(normalizeTld(ext.getTld()));
        c.setCategories(ext.getCategories());
        c.setAudience(ext.getAudience());
        c.setRegistrationPrice(ext.getRegistrationPrice());
        c.setRenewalPrice(ext.getRenewalPrice());
        c.setExtensionType(ext.getExtensionType());
        c.setHasRestrictions(ext.isHasRestrictions());
        c.setHasPremium(ext.isHasPremium());
        c.setRegistryName(ext.getRegistry());
        c.setMinRegistrationPeriod(ext.getMinRegistrationPeriod());
        c.setMaxRegistrationPeriod(ext.getMaxRegistrationPeriod());
        return c;
    }
}
