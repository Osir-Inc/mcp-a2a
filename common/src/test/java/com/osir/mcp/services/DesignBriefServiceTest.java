package com.osir.mcp.services;

import com.osir.mcp.models.design.DesignBriefResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DesignBriefServiceTest {

    private final DesignBriefService service = new DesignBriefService();

    @Test
    void render_valueFallbackAndNestedKey() {
        String out = DesignPromptRenderer.render("A={{a}} B={{b | \"fb\"}} C={{c.d}} E={{e | \"x\"}}",
                Map.of("a", "1", "c", Map.of("d", "deep"), "e", ""));
        assertEquals("A=1 B=fb C=deep E=x", out);
    }

    @Test
    void build_requiredOnly_usesFallbacks() {
        DesignBriefResult r = service.build("Bar Mediterran", "Seafood bar in Vlora", "Tourists", "book_appointment",
                "Book a table", null);
        assertTrue(r.success(), r.message());
        assertTrue(r.systemPrompt().contains("Business: Bar Mediterran"));
        assertTrue(r.systemPrompt().contains("job: get a booking / appointment"));
        assertTrue(r.systemPrompt().contains("you decide, max 6"));
        assertTrue(r.systemPrompt().contains("Primary color: not specified — choose"));
        assertTrue(r.systemPrompt().contains("no contact form"));
        assertTrue(r.editRules().contains("Bar Mediterran"));
        assertFalse(r.brief().containsKey("page_job_text"), "derived keys must not leak into the echoed brief");
    }

    @Test
    void build_withJson_rendersReadableStructures() {
        DesignBriefResult r = service.build("X", "y", "z", "other", "Go", """
                {"brand":{"primary_color":"#112233","references":[{"url":"https://a.example","what_you_like":"calm spacing"}]},
                 "content":{"services_or_products":[{"name":"Grill","description":"fresh fish","price":"12€"}],
                            "contact":{"phone":"+355 1","social":["ig","fb"]},
                            "testimonials":[{"quote":"Great","name":"Ana"}]},
                 "constraints":{"form_endpoint":"https://f.example/x","max_sections":4}}""");
        assertTrue(r.success(), r.message());
        String p = r.systemPrompt();
        assertTrue(p.contains("Primary color: #112233"));
        assertTrue(p.contains("https://a.example — likes: calm spacing"));
        assertTrue(p.contains("Grill — fresh fish (12€)"));
        assertTrue(p.contains("phone: +355 1; social: ig, fb"));
        assertTrue(p.contains("\"Great\" — Ana"));
        assertTrue(p.contains("action=\"https://f.example/x\""));
        assertTrue(p.contains("you decide, max 4"));
    }

    @Test
    void build_rejectsInvalidInput() {
        assertFalse(service.build("X", "y", "z", "nope", "Go", null).success());
        assertFalse(service.build("X", "y", "z", "other", "Go", "{\"brand\":{\"primary_color\":\"red\"}}").success());
        assertFalse(service.build("X", "y", "z", "other", "Go", "{\"language\":\"English\"}").success());
        assertFalse(service.build("X", "y", "z", "other", "Go", "{\"constraints\":{\"dark_mode\":\"yes\"}}").success());
        assertFalse(service.build("X", "y", "z", "other", "Go", "{\"sections\":\"hero\"}").success());
        DesignBriefResult r = service.build("X", "y", "z", "other", "Go",
                "{\"brand\":{\"references\":[{\"url\":\"https://stripe.com\"}]}}");
        assertFalse(r.success());
        assertTrue(r.message().contains("what_you_like"));
        assertFalse(service.build("X", "y", "z", "other", "Go", "{not json").success());
    }

    @Test
    void gate_normalizesLlmQuirks() {
        String n = StaticSiteGate.normalize("```html\n<html><h1>a</h1></html>\n```");
        assertTrue(n.startsWith("<!doctype html>\n<html>"));
        assertTrue(n.endsWith("</html>"));
        assertEquals("<!DOCTYPE html><html></html>", StaticSiteGate.normalize("<!DOCTYPE html><html></html>"));
    }

    @Test
    void gate_enforcesDesignContractOnlyWhenAsked() {
        String ok = "<!doctype html><html><head><link rel=\"stylesheet\" href=\"https://fonts.googleapis.com/css2?family=X\">"
                + "<style>@import url(https://fonts.googleapis.com/css2?family=Y);</style></head>"
                + "<body><h1>Hi</h1><script>fetch('/x')</script></body></html>";
        assertNull(StaticSiteGate.check(ok, true));
        assertNotNull(StaticSiteGate.check("<div>fragment</div>", true));
        assertNotNull(StaticSiteGate.check("<html><h1>a</h1><h1>b</h1></html>", true));
        assertTrue(StaticSiteGate.check("<html><h1>a</h1><script src=\"https://cdn.example/x.js\"></script></html>", true)
                .contains("cdn.example"));
        assertTrue(StaticSiteGate.check("<html><h1>a</h1><link href=\"//cdn.tailwindcss.com/x.css\"></html>", true)
                .contains("cdn.tailwindcss.com"));
        assertTrue(StaticSiteGate.check("<html><h1>a</h1><style>@import 'https://evil.example/a.css';</style></html>", true)
                .contains("evil.example"));
        assertNotNull(StaticSiteGate.check("<html><h1>a</h1><iframe src=\"x\"></iframe></html>", true));
        assertNotNull(StaticSiteGate.check("<html><h1>a</h1><object data=\"x\"></object></html>", true));
    }

    @Test
    void gate_userOwnSiteOnlyBasics() {
        // A user's own site may use CDNs, embeds, and several h1s — only the basics apply.
        String ownSite = "<html><h1>a</h1><h1>b</h1><script src=\"https://cdn.example/x.js\"></script>"
                + "<iframe src=\"https://youtube.com/embed/x\"></iframe></html>";
        assertNull(StaticSiteGate.check(ownSite, false));
        assertNotNull(StaticSiteGate.check("<div>fragment</div>", false));
        assertNotNull(StaticSiteGate.check("", false));
    }
}
