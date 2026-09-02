# TODO

## Agent-readiness (handoff 2026-09-01; §4b/§4d/§5 shipped 2026-09-02 when backend went live)

- [x] **`getHostingBundle` tool** — shipped (CatalogMCPServer, anonymous; passthrough model).
- [x] **`createPaymentSession` poll fields** — `pollTool`/`pollEndpoint`/`expiresAt` passed through; tool description explains the poll loop.
- [x] **Telemetry emission** — `telemetry/` package: every audited tool call → buffered event → batched POST /v1/agent/telemetry every 30 s (≤200/batch, drop on failure/overflow). **Disabled until `OSIR_TELEMETRY_API_KEY` is set — needs an api-user key issued for the MCP service (ask Armand/issue in Keycloak).**
- [ ] **Telemetry "registered" stage** — not observable in the audit interceptor (happens inside executeConfirmedAction's callback); emit from PendingActionStore if the funnel needs it beyond "confirmed".
- [ ] **Idempotency-Key pass-through** on mutating tools — blocked on backend ledger work (handoff §7).
- [ ] **Per-tool `osir:*` scope declarations** (handoff §7) — do together with the A.0 tool inventory; tokens already carry all 8 scopes since device login now requests them.

## Website design (osirSiteDesignBrief / osirSitePublish)

- [ ] **TODO(contact-form)** — build an OSIR form-handling endpoint (POST name/email/message → forward to the site owner's mailbox). Until then `DesignBriefService` defaults to `tel:`/`mailto:`/WhatsApp links and renders a `<form>` only when the client supplies `constraints.form_endpoint`. When it ships: set it as the default `form_endpoint`, flip the default to contact_form=true, drop the fallback line.
- [ ] **Screenshot critique (Prompt B from the design pack)** — extend C2's post-deploy QA (`osirAppStatus.qa`) to return screenshot URLs at 1280px and 390px, then add a step to the `website_designer` prompt: "look at the screenshots, list what looks templated/broken, fix, republish". No Playwright in this repo.
- [ ] **Rate limit on `osirSitePublish`** — same exposure as `osirAppDeploy`: a looping agent could redeploy every turn. Confirm C2 throttles per tenant; if not, add a `DestructiveOpRateLimiter` bucket (e.g. 10/min) on publish.
- [ ] **E2E test in Claude.ai** — `website_designer` → interview → `osirSitePublish` → `osirAppStatus` READY → open `liveUrl`; then one revision under the same name. Also try from the osir.com DeepSeek chat loop (tool results must reach the model).
- [ ] **Custom domains for `*.osir.app` static sites** — customers will ask ("I want it on my own domain"). Needs C2 route support for a customer domain → app mapping; the MCP side already has registerDomain + DNS tools to complete the story.
- [ ] Version history / revert beyond the last deploy — only if clients ask (`osirAppGetSource` covers the last version).

## MCP transport (found during e2e testing, 2026-08-31)

- [ ] **Frontend (osir.com chat) MCP client must re-initialize on session loss.** A server restart invalidates every Streamable HTTP session; the server answers `Mcp session not found`. Required client behavior (per the Streamable HTTP spec): on a POST rejected for a missing session, run a new `initialize` handshake and retry the call once — auth is unaffected (Bearer/sessionKey are stateless). Observed 2026-08-31: the DeepSeek loop retried a dead session for 6+ minutes and reported it as a "platform outage"; Claude.ai recovered after 2 attempts by re-initializing. Also cap MCP retries (2–3, then surface to the user).
- [ ] **Upgrade quarkus-mcp-server to 2.x** — adds the stateless `2026-07-28` protocol revision: restarts become invisible to clients, and the recurring `Invalid MCP protocol header: 2026-07-28` noise from Claude.ai disappears. Needs a compat spike (1.11.0 → 2.x API changes).

## Docs

- [ ] `PromptsMCPServerTest` hardcodes `assertEquals(8, …)` no-arg prompts — an implicit inventory; consider counting `@Prompt` annotations across all `*MCPServer` classes instead.
