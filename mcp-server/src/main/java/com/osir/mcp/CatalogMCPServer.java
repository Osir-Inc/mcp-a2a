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

    @Tool(description = "Get the complete product catalog including domain extensions, VPS packages, and dedicated servers. No authentication required.")
    public ProductCatalogResult getProductCatalog(McpConnection connection) {
        try {
            return catalogService.getProductCatalog();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving product catalog");
            return new ProductCatalogResult(false, "Failed to retrieve product catalog: " + e.getMessage());
        }
    }

    @Tool(description = "Get all available domain extensions (TLDs) with pricing information. No authentication required.")
    public DomainExtensionsResult getDomainExtensions(McpConnection connection) {
        try {
            return catalogService.getDomainExtensions();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving domain extensions");
            return new DomainExtensionsResult(false, "Failed to retrieve domain extensions: " + e.getMessage());
        }
    }

    @Tool(description = "Get all available dedicated server configurations with pricing and specifications. No authentication required.")
    public DedicatedServerCatalogResult getDedicatedServerCatalog(McpConnection connection) {
        try {
            return catalogService.getDedicatedServerCatalog();
        } catch (Exception e) {
            Log.errorf(e, "Error retrieving dedicated server catalog");
            return new DedicatedServerCatalogResult(false, "Failed to retrieve dedicated server catalog: " + e.getMessage());
        }
    }

    @Tool(description = """
            List TLDs from the OSIR catalog that have category and audience metadata \
            populated. Returns structured candidates only — no ranking, no scoring.

            USAGE PATTERN:
              1. Call this tool with optional structured filters (price cap, exclude \
                 ccTLDs, exclude restricted, etc.) based on what the user said.
              2. Examine the returned `candidates`. Each has `categories` and `audience` \
                 arrays you can match against the user's keywords and intent.
              3. Pick 3-6 TLDs based on relevance to the user's project. Show your \
                 reasoning to the user.
              4. Pass the chosen TLDs to bulkDomainSuggestions to find specific names.

            The categories vocabulary is controlled. Common values include: \
              generic, business, commerce, tech, dev, ai, software, web, mobile, \
              api, cloud, data, health, medical, pharma, clinical, wellness, fitness, \
              dental, finance, fintech, banking, education, academic, media, news, \
              design, art, creator, agency, community, social, blog, nonprofit, \
              personal, brand, marketplace, retail, music, audio, video, photo, \
              startup, infrastructure, journal.

            The audience vocabulary includes: \
              b2c, b2b, professional, developer, creator, enterprise, smb, consumer, startup.

            Note: registrationPrice and renewalPrice in each candidate are decimal \
              strings (e.g. "10.39"), not numbers.

            When the user's request is budget-conscious, set maxRegisterPrice. When \
            they explicitly say "no country domains", set excludeCcTLDs=true. When they \
            mention regulated industries casually (without intent to register a \
            restricted TLD), set excludeRestricted=true.

            Do NOT set excludePremium as a budget filter. hasPremium=true means the \
            TLD has registry-level premium pricing for a small subset of names — the \
            standard registrationPrice shown in the catalog applies to most names. \
            .app, .dev, and .tech are all hasPremium=true but register most names at \
            their standard price. Only set excludePremium=true when the user \
            explicitly asks for "no premium domains" or "no surprise pricing."

            Auth: not required.""")
    public CategorizedTldsResult listCategorizedTlds(
            @ToolArg(required = false) Boolean excludeRestricted,
            @ToolArg(required = false) Boolean excludeCcTLDs,
            @ToolArg(required = false) Double maxRegisterPrice,
            @ToolArg(required = false) Boolean excludePremium,
            @ToolArg(required = false) String registry,
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
                        Log.warnf("Malformed registration price for TLD '%s': '%s' — including in results",
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
