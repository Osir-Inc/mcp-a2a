package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.mcp.models.design.DesignBriefResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates a site design brief (hand-rolled rules mirroring design-brief.schema.json — no schema
 * library; add one if the rules outgrow this) and renders the design + edit prompts for the calling
 * LLM. Pure text: no backend, no auth.
 */
@ApplicationScoped
public class DesignBriefService {

    static final Map<String, String> PAGE_JOBS = Map.of(
            "get_contact", "get the visitor to contact us",
            "sell_product", "sell a product",
            "book_appointment", "get a booking / appointment",
            "collect_signups", "collect sign-ups",
            "inform_portfolio", "inform and show our work (portfolio)",
            "other", "other (see primary action)");
    static final Set<String> SITE_TYPES = Set.of("one_page", "landing", "multi_section_home", "portfolio", "shop_front");
    static final Set<String> TONES = Set.of("warm", "premium", "playful", "technical", "minimal", "bold");
    static final Set<String> ANIMATIONS = Set.of("none", "subtle", "expressive");
    private static final Pattern HEX = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern LANG = Pattern.compile("^[a-z]{2,3}(-[A-Za-z]{2,4})?$");
    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public DesignBriefResult build(String businessName, String whatItIs, String audience, String pageJob,
                                   String primaryAction, String briefJson) {
        Map<String, Object> brief;
        try {
            brief = briefJson == null || briefJson.isBlank()
                    ? new LinkedHashMap<>() : JSON.readValue(briefJson, LinkedHashMap.class);
        } catch (Exception e) {
            return DesignBriefResult.fail("briefJson is not valid JSON: " + e.getMessage());
        }
        brief.put("business_name", businessName);
        brief.put("what_it_is", whatItIs);
        brief.put("audience", audience);
        brief.put("page_job", pageJob);
        brief.put("primary_action", primaryAction);

        List<String> errors = validate(brief);
        if (!errors.isEmpty()) {
            return DesignBriefResult.fail("Brief invalid — fix and call again: " + String.join("; ", errors));
        }

        Map<String, Object> view = new LinkedHashMap<>(brief);
        view.put("page_job_text", PAGE_JOBS.get(pageJob));
        view.put("sections_text", sectionsText(brief));
        view.put("contact_form_line", contactFormLine(brief));
        view.put("brand", withText(brief.get("brand"), "references", DesignBriefService::referenceText));
        Object content = withText(brief.get("content"), "services_or_products", DesignBriefService::serviceText);
        content = withText(content, "testimonials", DesignBriefService::testimonialText);
        content = withText(content, "contact", DesignBriefService::contactText);
        view.put("content", content);

        String design = DesignPromptRenderer.render(DesignPromptRenderer.template("site-design.txt"), view);
        String edit = DesignPromptRenderer.render(DesignPromptRenderer.template("site-edit.txt"), view);
        return new DesignBriefResult(true,
                "Follow systemPrompt now and write the site. For every later change request follow editRules. "
                        + "Publish with osirSitePublish.",
                design, edit, brief);
    }

    // ---- human-readable rendering of structured fields -------------------------------------

    private static String sectionsText(Map<String, Object> b) {
        Object s = b.get("sections");
        Object max = DesignPromptRenderer.lookup(b, "constraints.max_sections");
        if (s instanceof List<?> l && !l.isEmpty()) {
            return l.stream().map(String::valueOf).collect(Collectors.joining(", "));
        }
        return "you decide, max " + (max instanceof Number n ? n.intValue() : 6);
    }

    /** Copy of {@code parent} with key {@code k} (if a list/map) replaced by a readable string. */
    @SuppressWarnings("unchecked")
    private static Object withText(Object parent, String k, java.util.function.Function<Object, String> f) {
        if (!(parent instanceof Map<?, ?> m) || m.get(k) == null) return parent;
        Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
        copy.put(k, f.apply(m.get(k)));
        return copy;
    }

    private static String items(Object v, java.util.function.Function<Map<?, ?>, String> one) {
        if (!(v instanceof List<?> l)) return String.valueOf(v);
        return l.stream().map(o -> o instanceof Map<?, ?> m ? one.apply(m) : String.valueOf(o))
                .collect(Collectors.joining("\n  - ", "\n  - ", ""));
    }

    private static String referenceText(Object v) {
        return items(v, m -> m.get("url") + " — likes: " + m.get("what_you_like"));
    }

    private static String serviceText(Object v) {
        return items(v, m -> m.get("name")
                + (m.get("description") != null ? " — " + m.get("description") : "")
                + (m.get("price") != null ? " (" + m.get("price") + ")" : ""));
    }

    private static String testimonialText(Object v) {
        return items(v, m -> "\"" + m.get("quote") + "\" — " + m.get("name"));
    }

    private static String contactText(Object v) {
        if (!(v instanceof Map<?, ?> m)) return String.valueOf(v);
        return m.entrySet().stream().filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + ": " + (e.getValue() instanceof List<?> l
                        ? l.stream().map(String::valueOf).collect(Collectors.joining(", ")) : e.getValue()))
                .collect(Collectors.joining("; "));
    }

    // TODO(contact-form): OSIR has no form-handling endpoint yet. Until it exists the default is
    // links only; a form is rendered only when the client supplies constraints.form_endpoint.
    private static String contactFormLine(Map<String, Object> brief) {
        Object endpoint = DesignPromptRenderer.lookup(brief, "constraints.form_endpoint");
        if (endpoint instanceof String s && !s.isBlank()) {
            return "render a <form method=\"post\" action=\"" + s + "\"> with name, email, message; "
                    + "no JS required to submit.";
        }
        return "no contact form — use tel:, mailto: and WhatsApp (wa.me) links from the contact details.";
    }

    // ---- validation --------------------------------------------------------------------------

    static List<String> validate(Map<String, Object> b) {
        List<String> errs = new ArrayList<>();
        for (String k : List.of("business_name", "what_it_is", "audience", "primary_action")) {
            if (!(b.get(k) instanceof String s) || s.isBlank()) errs.add(k + " is required");
        }
        enumCheck(errs, b, "page_job", PAGE_JOBS.keySet(), true);
        enumCheck(errs, b, "site_type", SITE_TYPES, false);
        enumCheck(errs, b, "tone", TONES, false);
        enumCheck(errs, b, "constraints.animations", ANIMATIONS, false);
        for (String k : List.of("brand.primary_color", "brand.secondary_color")) {
            Object v = DesignPromptRenderer.lookup(b, k);
            if (v != null && !(v instanceof String s && HEX.matcher(s).matches())) {
                errs.add(k + " must be a 6-digit hex like #1A2B3C");
            }
        }
        Object lang = b.get("language");
        if (lang != null && !(lang instanceof String s && LANG.matcher(s).matches())) {
            errs.add("language must be an ISO code like en, sq, de");
        }
        Object dark = DesignPromptRenderer.lookup(b, "constraints.dark_mode");
        if (dark != null && !(dark instanceof Boolean)) errs.add("constraints.dark_mode must be true/false");
        for (String k : List.of("sections", "mood_words", "brand.fonts", "content.image_urls")) {
            Object v = DesignPromptRenderer.lookup(b, k);
            if (v != null && !(v instanceof List<?>)) errs.add(k + " must be an array of strings");
        }
        if (DesignPromptRenderer.lookup(b, "mood_words") instanceof List<?> l && l.size() > 5) {
            errs.add("mood_words: max 5");
        }
        if (DesignPromptRenderer.lookup(b, "brand.references") instanceof List<?> refs) {
            if (refs.size() > 3) errs.add("brand.references: max 3");
            for (Object r : refs) {
                if (!(r instanceof Map<?, ?> m) || blank(m.get("url")) || blank(m.get("what_you_like"))) {
                    errs.add("brand.references: each needs {url, what_you_like} — naming a site alone "
                            + "does not transfer; say what about it the client likes");
                    break;
                }
            }
        }
        if (DesignPromptRenderer.lookup(b, "content.testimonials") instanceof List<?> ts) {
            for (Object t : ts) {
                if (!(t instanceof Map<?, ?> m) || blank(m.get("quote")) || blank(m.get("name"))) {
                    errs.add("content.testimonials: each needs {quote, name} — real ones only");
                    break;
                }
            }
        }
        return errs;
    }

    private static void enumCheck(List<String> errs, Map<String, Object> b, String key, Set<String> allowed,
                                  boolean required) {
        Object v = DesignPromptRenderer.lookup(b, key);
        if (v == null) {
            if (required) errs.add(key + " is required, one of " + allowed);
            return;
        }
        if (!(v instanceof String s) || !allowed.contains(s)) errs.add(key + " must be one of " + allowed);
    }

    private static boolean blank(Object o) {
        return !(o instanceof String s) || s.isBlank();
    }
}
