package com.osir.mcp;

import com.osir.mcp.models.deploy.DeployDtos.DeployResult;
import com.osir.mcp.models.design.DesignBriefResult;
import com.osir.mcp.security.McpAudited;
import com.osir.mcp.security.RequiresAuth;
import com.osir.mcp.services.DeploymentService;
import com.osir.mcp.services.DesignBriefService;
import io.quarkiverse.mcp.server.McpConnection;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Website design via the CALLING LLM. The server never calls a model: osirSiteDesignBrief returns
 * the design prompt as a tool result (works in any client, Claude.ai, our own chat, Claude Code),
 * the LLM writes the HTML in-conversation, and osirSitePublish ships it to *.osir.app.
 */
@McpAudited
@ApplicationScoped
public class WebsiteDesignMCPServer {

    @Inject
    DesignBriefService designBriefService;

    @Inject
    DeploymentService deploymentService;

    @Prompt(name = "website_designer",
            description = "Design and publish a website for the user with OSIR: interview, design, revise, publish.")
    public PromptMessage websiteDesigner() {
        return PromptMessage.withUserRole(new TextContent("""
            You are helping me design and publish a website with OSIR.

            1. INTERVIEW (conversational, not a form). Ask for these first, one or two at a time:
               - business name; what it actually does or sells (concrete); who visits the site and why;
               - the page's single job (get_contact | sell_product | book_appointment | collect_signups | inform_portfolio | other);
               - the one primary action / button text (e.g. "Book a table").
               Then offer the optional extras in ONE batch and let me skip: logo URL, brand colours (hex),
               fonts, existing site, up to 3 sites I like AND what I like about each, things to avoid,
               real content (tagline, services with prices, about text, contact details, my own photo URLs,
               real testimonials only), site language, tone, mood words, dark mode, animation level.
               Do not ask me for a famous site to copy; if I name one, ask what about it I like.

            2. BRIEF. Call osirSiteDesignBrief with the required fields and briefJson for the extras.
               If it returns success=false, fix the brief and call again.

            3. DESIGN + PREVIEW. Follow the returned systemPrompt exactly and write the complete HTML, never
               pasted into the chat as plain text. If you can render a live HTML preview (e.g. an artifact),
               iterate there and publish when I approve; otherwise call osirSitePublish(name, html,
               designContract: true) straight away, poll osirAppStatus until READY, and give me the liveUrl as
               the preview together with the summary lines the prompt asks for.

            4. REVISE. For each change I ask for, follow the returned editRules: republish with the same name,
               the full updated document, and designContract: true, then tell me what changed and the URL.

            (If I already HAVE a website, my own HTML, skip all of the above: just call
            osirSitePublish(name, html) without designContract; for a multi-file site use
            osirAppCreateUpload + osirAppDeploy with a zip.)
            """));
    }

    @Tool(name = "osirSiteDesignBrief",
            description = "osirSiteDesignBrief: Step 1 of designing a NEW website with OSIR. Validates the brief and returns "
                    + "'systemPrompt', the structured design brief and constraints YOU must then follow to write "
                    + "one complete self-contained HTML page, plus 'editRules' for later revisions. Call it before "
                    + "osirSitePublish for a new site, then publish the finished page with osirSitePublish. "
                    + "No authentication needed.",
            annotations = @Tool.Annotations(
                    title = "Create site design brief",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false))
    public DesignBriefResult osirSiteDesignBrief(
            @ToolArg(description = "The business or project name.") String businessName,
            @ToolArg(description = "What the business concretely does or sells.") String whatItIs,
            @ToolArg(description = "Who visits the site and why.") String audience,
            @ToolArg(description = "The page's single job: get_contact, sell_product, book_appointment, collect_signups, inform_portfolio, or other.") String pageJob,
            @ToolArg(description = "The one primary call to action, e.g. 'Book a table'.") String primaryAction,
            @ToolArg(required = false, description = "Optional JSON object with extras the user provided: site_type, sections[], language (ISO code, default en), tone (warm|premium|playful|technical|minimal|bold), mood_words[] (max 5), brand{logo_url, primary_color '#RRGGBB', secondary_color, fonts[], existing_site_url, references[{url, what_you_like}] (max 3; direction only, never copied), dislikes}, content{tagline, services_or_products[{name,description,price}], about_text, contact{phone,email,address,hours,social[]}, image_urls[], testimonials[{quote,name}] (real only)}, constraints{dark_mode, animations (none|subtle|expressive), form_endpoint, legal_footer}. Ask the user rather than inventing values; skipped extras are fine.") String briefJson) {
        try {
            return designBriefService.build(businessName, whatItIs, audience, pageJob, primaryAction, briefJson);
        } catch (Exception e) {
            Log.errorf(e, "osirSiteDesignBrief error: %s", e.getMessage());
            return DesignBriefResult.fail("Failed to build the design brief: " + e.getMessage());
        }
    }

    @RequiresAuth
    @Tool(name = "osirSitePublish",
            description = "osirSitePublish: Publish a single-page website to a live HTTPS URL on Osir (free tier). ANY complete "
                    + "HTML document works: the user's own site, a page designed in this chat, or one from the "
                    + "osirSiteDesignBrief flow. Calling again with the same name redeploys the new version. For "
                    + "MULTI-FILE sites (separate CSS/JS/images) use osirAppCreateUpload + osirAppDeploy with a "
                    + "zip instead. Then poll osirAppStatus until READY. Requires authentication.",
            annotations = @Tool.Annotations(
                    title = "Publish a website",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false))
    public DeployResult osirSitePublish(
                                        @ToolArg(description = "Site name: lowercase letters, digits, and hyphens, e.g. 'bar-mediterran'.") String name,
                                        @ToolArg(description = "The complete <html> document to publish (max 1 MiB).") String html,
                                        @ToolArg(required = false, description = "Region: 'us' or 'al' ('al' is Albania/Tirana).") String region,
                                        @ToolArg(required = false, description = "Set true ONLY for pages generated via the osirSiteDesignBrief flow; additionally enforces its output contract (exactly one <h1>, self-contained, no external scripts/CSS except Google Fonts, no iframes). Never set it for a user's own site.") Boolean designContract,
                                        @ToolArg(name = RequiresAuth.SESSION_KEY, description = RequiresAuth.SESSION_KEY_DESC, required = false) String sessionKey,
                                        McpConnection connection) {
        try {
            return deploymentService.publishStatic(name, html, region, Boolean.TRUE.equals(designContract));
        } catch (Exception e) {
            Log.errorf(e, "osirSitePublish error: %s", e.getMessage());
            return DeployResult.fail("Publish failed: " + e.getMessage());
        }
    }
}
