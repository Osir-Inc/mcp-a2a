package com.osir.mcp.services;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Trust-boundary checks on LLM-written HTML before it goes live: enforces the parts of the design
 * output contract a machine can check (self-contained, no third-party scripts/embeds, one h1).
 * Returns a precise problem string, or null when the document passes.
 *
 * <p>Best-effort by design: it keeps the LLM honest about the contract; it is NOT the security
 * layer (that is C2's microVM isolation and platform abuse policy). Inline fetch() etc. pass.
 */
final class StaticSiteGate {

    static final int MAX_BYTES = 1024 * 1024;
    private static final Pattern H1 = Pattern.compile("<h1[\\s>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCRIPT_SRC = Pattern.compile("<script[^>]*\\ssrc\\s*=\\s*[\"']([^\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_HREF = Pattern.compile("<link[^>]*\\shref\\s*=\\s*[\"'](?:https?:)?//([^\"'/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_IMPORT = Pattern.compile("@import\\s+(?:url\\()?[\"']?(?:https?:)?//([^\"')\\s/]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern EMBED = Pattern.compile("<(iframe|object|embed)[\\s>]", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("^\\s*```[a-zA-Z]*\\s*|\\s*```\\s*$");

    private StaticSiteGate() {
    }

    /** Tolerate common LLM output quirks: markdown fences around the document, missing doctype. */
    static String normalize(String html) {
        if (html == null) return null;
        String out = FENCE.matcher(html).replaceAll("").strip();
        if (!out.regionMatches(true, 0, "<!doctype", 0, 9)) out = "<!doctype html>\n" + out;
        return out;
    }

    /**
     * @param designContract false = user's own site: only the basics (non-empty, size, complete
     *                       document). true = LLM-designed page: additionally enforce the design
     *                       output contract (one h1, self-contained, no embeds).
     */
    static String check(String html, boolean designContract) {
        if (html == null || html.isBlank()) return "html is empty";
        if (html.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_BYTES) return "html exceeds 1 MiB";
        String lower = html.toLowerCase();
        if (!lower.contains("<html") || !lower.contains("</html>")) return "not a complete document (<html>…</html>)";
        if (!designContract) return null;
        int h1 = 0;
        for (Matcher m = H1.matcher(html); m.find(); ) h1++;
        if (h1 != 1) return "exactly one <h1> required, found " + h1;
        Matcher e = EMBED.matcher(html);
        if (e.find()) return "<" + e.group(1).toLowerCase() + "> is not allowed (no third-party embeds)";
        Matcher s = SCRIPT_SRC.matcher(html);
        if (s.find()) return "external <script src=\"" + s.group(1) + "\"> is not allowed — inline all JS";
        for (Pattern p : new Pattern[]{LINK_HREF, CSS_IMPORT}) {
            Matcher l = p.matcher(html);
            while (l.find()) {
                String host = l.group(1).toLowerCase();
                if (!host.equals("fonts.googleapis.com") && !host.equals("fonts.gstatic.com")) {
                    return "external stylesheet from " + host + " is not allowed — only Google Fonts; inline all CSS";
                }
            }
        }
        return null;
    }
}
