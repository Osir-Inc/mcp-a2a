package com.osir.mcp.services;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{key.path | "fallback"}}} placeholders against a nested map. Strings are inserted
 * as-is; other non-empty values (lists, maps, booleans) are inserted as compact JSON, which LLMs
 * read fine; null/blank/empty → the fallback (or "" if none). Fallbacks stay in the prompt on
 * purpose: models behave better told explicitly that something was not provided.
 */
public final class DesignPromptRenderer {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{\\s*([\\w.]+)\\s*(?:\\|\\s*\"([^\"]*)\")?\\s*}}");
    private static final ObjectMapper JSON = new ObjectMapper();

    private DesignPromptRenderer() {
    }

    public static String render(String template, Map<String, Object> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            Object v = lookup(values, m.group(1));
            String text = isEmpty(v) ? (m.group(2) == null ? "" : m.group(2)) : stringify(v);
            m.appendReplacement(out, Matcher.quoteReplacement(text));
        }
        m.appendTail(out);
        return out.toString();
    }

    public static String template(String name) {
        try (InputStream in = DesignPromptRenderer.class.getResourceAsStream("/prompts/" + name)) {
            if (in == null) throw new IllegalStateException("missing prompt template " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read prompt template " + name, e);
        }
    }

    @SuppressWarnings("unchecked")
    static Object lookup(Map<String, Object> values, String path) {
        Object cur = values;
        for (String part : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> map)) return null;
            cur = ((Map<String, Object>) map).get(part);
        }
        return cur;
    }

    private static boolean isEmpty(Object v) {
        return v == null
                || (v instanceof String s && s.isBlank())
                || (v instanceof Collection<?> c && c.isEmpty())
                || (v instanceof Map<?, ?> m && m.isEmpty());
    }

    private static String stringify(Object v) {
        if (v instanceof String s) return s;
        try {
            return JSON.writeValueAsString(v);
        } catch (IOException e) {
            return String.valueOf(v);
        }
    }
}
