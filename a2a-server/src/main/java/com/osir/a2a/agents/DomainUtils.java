package com.osir.a2a.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared domain name utilities used by agents.
 */
public final class DomainUtils {

    public static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "\\b([a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.(?:[a-zA-Z]{2,}))\\b"
    );

    private static final Set<String> FILE_EXTENSIONS = Set.of(
            "java", "go", "js", "ts", "py", "md", "txt", "json", "xml", "yml", "yaml", "html", "css"
    );

    private DomainUtils() {}

    public static String extractFirst(String text) {
        Matcher m = DOMAIN_PATTERN.matcher(text);
        while (m.find()) {
            String c = m.group(1).toLowerCase();
            String tld = c.substring(c.lastIndexOf('.') + 1);
            if (FILE_EXTENSIONS.contains(tld)) continue;
            return c;
        }
        return null;
    }

    public static List<String> extractAll(String text) {
        List<String> domains = new ArrayList<>();
        Matcher m = DOMAIN_PATTERN.matcher(text);
        while (m.find()) {
            String c = m.group(1).toLowerCase();
            String tld = c.substring(c.lastIndexOf('.') + 1);
            if (!FILE_EXTENSIONS.contains(tld)) domains.add(c);
        }
        return domains;
    }
}
