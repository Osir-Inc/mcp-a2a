package com.osir.mcp.models.contact;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;

/** Registry-specific contact fields; today only .al (AKEP) needs them. Backend stores them as a string map. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RegistryAttributes {
    @JsonPropertyDescription("AKEP entity type, exactly one of: 'Person Natyror' (private person), 'Person Fizik Tregtar' (sole trader), "
            + "'Person Juridik Privat' (company), 'Person Juridik Publik', 'Institucion Publik'.")
    private String entityType;
    @JsonPropertyDescription("Identity document type, exactly one of: 'op' (ID card), 'passport', 'mpsv', 'ico' (company registration document). "
            + "For a company, the director's ID or passport.")
    private String doctype;
    @JsonPropertyDescription("Number of that document.")
    private String docnr;
    @JsonPropertyDescription("Tax/VAT id (NIPT). Required for every entityType except 'Person Natyror'.")
    private String nipt;

    public RegistryAttributes() {}

    public RegistryAttributes(String entityType, String doctype, String docnr, String nipt) {
        this.entityType = entityType;
        this.doctype = doctype;
        this.docnr = docnr;
        this.nipt = nipt;
    }

    public static final List<String> ENTITY_TYPES = List.of("Person Natyror", "Person Fizik Tregtar",
            "Person Juridik Privat", "Person Juridik Publik", "Institucion Publik");
    public static final List<String> DOCTYPES = List.of("op", "passport", "mpsv", "ico");

    /** What keeps these from being valid AKEP registrant fields (missing or not an allowed value); empty when valid. */
    public static List<String> missingForAl(RegistryAttributes a) {
        List<String> problems = new ArrayList<>();
        if (a == null) a = new RegistryAttributes();
        if (blank(a.entityType)) problems.add("entityType missing");
        else if (!ENTITY_TYPES.contains(a.entityType)) problems.add("entityType must be one of " + ENTITY_TYPES);
        if (blank(a.doctype)) problems.add("doctype missing");
        else if (!DOCTYPES.contains(a.doctype)) problems.add("doctype must be one of " + DOCTYPES);
        if (blank(a.docnr)) problems.add("docnr missing");
        if (!"Person Natyror".equals(a.entityType) && blank(a.nipt)) problems.add("nipt missing");
        return problems;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getDoctype() { return doctype; }
    public void setDoctype(String doctype) { this.doctype = doctype; }

    public String getDocnr() { return docnr; }
    public void setDocnr(String docnr) { this.docnr = docnr; }

    public String getNipt() { return nipt; }
    public void setNipt(String nipt) { this.nipt = nipt; }
}
