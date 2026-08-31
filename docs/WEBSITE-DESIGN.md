# Website Design with the OSIR Assistant

Design and publish a real website by talking to an AI — no builder, no templates, no code shown to the customer. The AI that the customer is already chatting with (Claude.ai, the osir.com chat, or any MCP-capable client) does the designing; OSIR validates the brief, supplies the design instructions, and hosts the result at `https://<name>.osir.app`.

**The server never calls a model.** `osirSiteDesignBrief` returns the design instructions *as a tool result*; whatever LLM made the call follows them and writes one self-contained HTML page; `osirSitePublish` checks it and puts it live. That is why the same feature works identically behind Claude, DeepSeek, or anything else.

```
Customer ──chat──▶ LLM ──osirSiteDesignBrief──▶ MCP server (validate brief, return design prompt)
                   LLM  writes the HTML (never pasted into the chat)
                   LLM ──osirSitePublish──────▶ MCP server (gate → zip → deploy) ──▶ https://name.osir.app
                   LLM ──osirAppStatus────────▶ READY + QA verdict
Customer ◀─"here's your live preview: https://name.osir.app"──┘
```

---

## Part 1 — For customers

*(This section is written so it can be lifted into the help center almost as-is.)*

### What you can make

One-page websites and landing pages: a restaurant page, a portfolio, a small shop front, a service business, an event page. The site is live on a free `yourname.osir.app` address the moment it's designed — the preview **is** the real site.

### How it works

1. **Tell the assistant what you need.** In your own words, in your own language:
   > "Make a website for my seafood restaurant in Vlora."
2. **Answer a few questions.** The assistant asks what the business does, who visits the site, what the one most important action is (book a table? call you? buy?). Then it offers extras in one batch — logo, brand colours, your photos, real text, opening hours — all skippable.
3. **Get a live preview.** The assistant designs the page and gives you a link like `https://bar-mediterran.osir.app`. Open it on your phone and your laptop — that's the actual site.
4. **Ask for changes, plainly.** "Make the header darker." "The phone number is wrong, it's 069…". "Add the lunch menu." Each change goes live at the same link in under a minute.
5. **Done is done.** The link is yours. Come back any time and ask for more changes.

### Tips for a better result

- **Real content beats invented content.** Give your actual tagline, prices, opening hours, photos (as URLs). The assistant only invents what's missing, and marks placeholders visibly.
- **Don't say "make it like stripe.com".** The assistant can't see other sites and won't copy them. What works: *"I like example.com — the calm spacing and how the photos are large."* Name the site **and what you like about it** (up to 3 references).
- **Mood words help.** "handmade, Mediterranean, honest" steers the design more than "modern and professional" (everything claims to be that).
- **One job per page.** The clearer you are about the single thing the page must achieve, the sharper the design.

### What the assistant will not do

- Invent testimonials, reviews, ratings, awards, or client logos — only real ones you provide appear.
- Use other companies' logos or characters, or copy a famous site's look.
- Add tracking scripts or third-party widgets. The page is fast, self-contained, and accessible (works on a 360px phone, keyboard-navigable, honors reduced-motion).

Contact forms: for now the site uses call / e-mail / WhatsApp buttons (or your own form endpoint if you have one, e.g. Formspree). A built-in OSIR contact form is on the roadmap.

### Practical use cases

| Customer says | What happens |
|---|---|
| "Faqe interneti për restorantin tim në Vlorë, rezervime me telefon" | Interview in Albanian → one-page site in Albanian, tel: CTA "Rezervo një tavolinë", live at `restorant-x.osir.app` |
| "Portfolio for my wedding photography, here are 6 photo URLs" | `inform_portfolio` brief → gallery-led design using the real photos, "Enquire about a date" mailto CTA |
| "Landing page for our accounting office in Vienna, German, we need the Impressum" | `language: de`, `legal_footer` with the registration text, "Termin vereinbaren" CTA |
| "We're launching a padel club, collect interested people" | `collect_signups` → sign-up oriented page; form posts to the customer's endpoint if given, otherwise WhatsApp/e-mail links |
| "Change the photo in the hero and make prices bold" | Edit flow — only those two things change, same URL |
| "I already have a website — here's my index.html" | No design flow at all: `osirSitePublish` puts it live as-is (own sites may use CDNs, embeds, anything). A multi-file site goes up as a zip |
| "I want it on my own domain" | Register the domain in the same chat (`registerDomain`, DNS tools). Pointing a custom domain at a `*.osir.app` site is on the roadmap (see TODO) |

---

## Part 2 — For the frontend team (osir.com chat integration)

### What to expose

Give your agent loop these MCP tools (names as served at `/mcp`):

| Tool | Auth | Purpose |
|---|---|---|
| `osirSiteDesignBrief` | none | Validate the brief → returns `systemPrompt` (the design instructions the model must follow), `editRules` (revision rules), `brief` (normalized echo) |
| `osirSitePublish` | Bearer / sessionKey | **Standalone** — publishes ANY complete single-page HTML document (the customer's own site, a page designed anywhere in the chat, or output of the design flow). `name` + full `html` → gate, zip, deploy. Same `name` = redeploy. Pass `designContract: true` only for pages from the design flow. Multi-file sites: `osirAppCreateUpload` + `osirAppDeploy` with a zip |
| `osirAppStatus` | Bearer / sessionKey | Poll until `READY`; `qa` is a black-box check of the live page |
| `osirAppList`, `osirAppGetSource` | Bearer / sessionKey | Optional: list the customer's sites; retrieve last deployed HTML |

The MCP prompt `website_designer` contains the full interview + flow script. If your client supports MCP prompts, inject it when the user enters the "design a website" flow; if not (plain function-calling loop), copy its text into your system prompt for that flow — it's the same content.

### The loop, step by step

1. User asks for a website. Model interviews (5 required fields, optionals in one batch).
2. Model calls `osirSiteDesignBrief(businessName, whatItIs, audience, pageJob, primaryAction, briefJson?)`.
   - `success:false` → the `message` is written to be fed straight back to the model; it fixes the brief and retries. Don't surface it to the customer.
3. Model follows `systemPrompt` and writes the complete HTML **into the `osirSitePublish` tool call** — the prompts explicitly forbid pasting HTML into the chat (halves token cost; the live URL is the preview).
4. `osirSitePublish` returns `appId` + `liveUrl` → model polls `osirAppStatus` until `READY`, then hands the customer the URL.
5. Revision: model applies `editRules` to the HTML (still in its context from its own previous tool call) and calls `osirSitePublish` again with the same `name`.

### Contract and limits (what publish enforces)

`osirSitePublish` is **not** tied to the design flow — customers can publish their own HTML as-is. The gate therefore has two levels; rejections carry a machine-actionable message the model should see and act on:

**Always (any site):** must be a complete `<html>…</html>` document, ≤ 1 MiB. Markdown fences and a missing doctype are tolerated (normalized server-side).

**Only with `designContract: true`** (set by the design-flow prompts, never for a customer's own site): exactly one `<h1>`; no `<iframe>/<object>/<embed>`; no external `<script src>`; external CSS only from Google Fonts. A customer's own site with CDN scripts or a YouTube embed publishes fine without the flag.

The gate is contract enforcement, not the security layer (C2's microVM isolation is). Multi-file sites (separate CSS/JS/images) go through the existing zip route: `osirAppCreateUpload` → PUT the zip → `osirAppDeploy`.

### Transport requirement: survive server restarts

Streamable HTTP sessions live in the server's memory. After a redeploy every session is gone and POSTs answer `Mcp session not found`. Your MCP client **must** react by running a fresh `initialize` and retrying the call once — not by retrying the dead session (that presents as a permanent "endpoint down"). Auth is unaffected: Bearer / `sessionKey` are stateless. Cap retries at 2–3, then tell the user.

### UX recommendations

- **Embed the preview.** The generated sites contain no frame-busting; render `liveUrl` in an `<iframe>` in your chat UI next to the conversation, and reload it after each successful publish. Add a mobile/desktop width toggle (390 / 1280) — cheap and customers love it.
- **Show publish progress.** `osirAppStatus.deploymentState` → a small "Publishing… / Live ✓" indicator. If `qa.status == FAILED`, feed `qa.findings` back to the model automatically rather than showing raw errors.
- **Session auth.** `osirSiteDesignBrief` works logged-out — let anonymous visitors design; require login (KeyCloak device flow / Bearer) only at the publish step. That's a natural conversion point.
- **Model choice.** Any tool-calling model works. The design prompt does the heavy lifting; stronger models produce notably better first drafts (the prompt pack suggested an Opus-class model for the first generation).

### Error handling summary

| Result | Do |
|---|---|
| `osirSiteDesignBrief success:false` | Feed `message` to the model; it re-asks the customer or fixes the JSON |
| `osirSitePublish success:false` (gate) | Feed `message` to the model; it fixes the HTML and retries |
| `osirSitePublish success:false` (auth) | Trigger login flow, retry after |
| `osirAppStatus qa FAILED` | Feed `qa.findings` to the model → fix → republish |

### Related docs

- [GUIDE.md](GUIDE.md) — end-to-end walkthrough among the other tools
- [MCP-TOOL-EXAMPLES.md](MCP-TOOL-EXAMPLES.md) — full tool inventory
- [TODO.md](TODO.md) — open items: OSIR contact-form endpoint, screenshot critique via C2 QA, publish rate limit, custom domains for `*.osir.app` sites
