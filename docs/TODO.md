# TODO

## Agent-readiness (handoff 2026-09-01; §4b/§4d/§5 shipped 2026-09-02 when backend went live)

- [x] **`getHostingBundle` tool** — shipped (CatalogMCPServer, anonymous; passthrough model).
- [x] **`createPaymentSession` poll fields** — `pollTool`/`pollEndpoint`/`expiresAt` passed through; tool description explains the poll loop.
- [x] **Telemetry emission** — `telemetry/` package: every audited tool call → buffered event → batched POST /v1/agent/telemetry every 30 s (≤200/batch, drop on failure/overflow). `OSIR_TELEMETRY_API_KEY` is set in production (2026-09-04), so emission is live from the next deploy — confirm events land after it.
- [ ] **Telemetry "registered" stage** — not observable in the audit interceptor (happens inside executeConfirmedAction's callback); emit from PendingActionStore if the funnel needs it beyond "confirmed".
- [ ] **Idempotency-Key pass-through** on mutating tools — blocked on backend ledger work (handoff §7).
- [ ] **Per-tool `osir:*` scope declarations** (handoff §7) — do together with the A.0 tool inventory; tokens already carry all 8 scopes since device login now requests them.

## Website design (osirSiteDesignBrief / osirSitePublish)

- [ ] **TODO(contact-form)** — build an OSIR form-handling endpoint (POST name/email/message → forward to the site owner's mailbox). Until then `DesignBriefService` defaults to `tel:`/`mailto:`/WhatsApp links and renders a `<form>` only when the client supplies `constraints.form_endpoint`. When it ships: set it as the default `form_endpoint`, flip the default to contact_form=true, drop the fallback line.
- [ ] **Screenshot critique (Prompt B from the design pack)** — extend C2's post-deploy QA (`osirAppStatus.qa`) to return screenshot URLs at 1280px and 390px, then add a step to the `website_designer` prompt: "look at the screenshots, list what looks templated/broken, fix, republish". No Playwright in this repo.
- [x] **Rate limit on `osirSitePublish`** (2026-09-04) — C2 throttles nothing (no rate limiting in the deploy backend at all), so `DestructiveOpRateLimiter` grew a `PUBLISH` bucket, 10/min per connection, checked before the deploy. `osirAppDeploy` is left ungated on purpose: it needs an upload ticket and a zip PUT first, which a looping model does not produce by accident. Revisit if that stops being true.
- [ ] **E2E test in Claude.ai** — `website_designer` → interview → `osirSitePublish` → `osirAppStatus` READY → open `liveUrl`; then one revision under the same name. Also try from the osir.com DeepSeek chat loop (tool results must reach the model).
- [ ] **Custom domains for `*.osir.app` static sites** — customers will ask ("I want it on my own domain"). Needs C2 route support for a customer domain → app mapping; the MCP side already has registerDomain + DNS tools to complete the story.
- [ ] Version history / revert beyond the last deploy — only if clients ask (`osirAppGetSource` covers the last version).

## A2A (needs a decision, not just code)

- [x] **A2A confirmation gate — Layer A** (2026-09-04) — `ConfirmationGate` stages every billable or
  destructive skill on all five agents that had one: order/build/delete VPS, register/renew/transfer
  domain, pay invoice, create payment, delete contact, delete DNS record. Staging returns
  INPUT_REQUIRED with a summary; the caller confirms by echoing the actionId on the SAME task.
  Single-use, 5-minute expiry, caller matched on the JWT subject, rate limited per bucket.
  **It is not a security control** — see the spec's §2 and the class javadoc: a caller holding the
  actionId can simply send it back. What it buys is audit, blast radius, and one mechanism across
  both transports.
- [ ] **A2A gate — Layer B: per-tool `osir:*` scopes.** THE control that actually stops an unattended
  caller from spending: a token without `osir:vps.write` / `osir:billing.write` cannot order however
  many confirmations it sends. Blocked on Keycloak scope definitions (B.2), same item as the
  agent-readiness scope work.
- [ ] **A2A gate — Layer C: a backend spend/velocity cap.** Neither transport can enforce this
  credibly (multiple instances, restarts, and the REST API is reachable directly).
- [ ] **A2A gate — Layer D: out-of-band approval**, if a real human-in-the-loop is wanted for agent
  callers: the approval has to reach a channel the CALLING agent does not control (the account
  owner's email or the portal). Note the existing push-notification webhook is caller-supplied, so it
  is not that channel.

## MCP transport

- [x] **Upgrade quarkus-mcp-server to 2.0.0** (done 2026-09-02) — source-compatible; one merged `-http` artifact serves Streamable at `/mcp` and legacy SSE at `/mcp/sse`; the published `/mcp/http` URL is preserved by `McpHttpPathCompatFilter`. `streamable.auto-init=true` makes stale sessions auto-initialize (verified: bogus `Mcp-Session-Id` gets a valid response, no more "Mcp session not found") - server restarts no longer strand clients.
- [ ] **Frontend (osir.com chat) MCP client should still cap retries and re-initialize on errors** (2-3 attempts, then surface to the user) - good hygiene even though the server-side auto-init has removed the "session not found" failure class observed 2026-08-31/2026-09-02.

## Docs

- [x] `PromptsMCPServerTest` no longer hardcodes a prompt count (2026-09-04) — it asserts every no-arg `PromptMessage` method is annotated `@Prompt` and returns content, which is the invariant the number was standing in for.

## Health

- [x] **`McpHealthCheck` told the truth** (2026-09-04) — it reported `version 1.0.0` / `protocol MCP 2025-03-26` on a 2.3.0 Streamable server and `testBackendConnection()` was a `return true` with a TODO. It now reports the configured version and a real (10 s-cached, 2 s-timeout) HEAD probe of the domain backend as `backend: reachable|unreachable`. Readiness deliberately stays UP on a backend blip — a DOWN would pull the MCP from the load balancer and turn a degraded backend into a full outage.
