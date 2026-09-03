# Part A Plan — OSIR Agents (MCP + A2A)

Response to Agent-Readiness Brief v2 (2026-09-01). Backend (Part B) and marketing (Part C) run in parallel; this covers Part A only.

## What the audit missed — current repo state

| Finding | Reality in this repo |
|---|---|
| F1 (availability lies when anonymous) | **MCP bug — backend is right that the body isn't theirs.** `DomainService.checkAvailability` (and `bulkCheckAvailability`, plus the chat-path `SessionAwareDomainService`) short-circuits on `!authService.isAuthenticated()` and fabricates `available: false, "Authentication required", isError: false` without ever calling the backend. Verified live (2026-09-01): backend `/v2/domains/{d}/available` does 401 anonymously, but `/namesuggestions/*` incl. `keyword-availability` answer 200 with no auth. Fix (MCP, no backend dependency): drop the pre-auth gate; authenticated → `/v2` as today; anonymous → public `keyword-availability` endpoint with list price + `priceType: "list"` note; and never map an auth/backend failure to `available: false` — return a real error `{code, message, resolution}`. Backend opening `/v2/…/available` (B.1) then just upgrades anonymous answers to the richer response. Regression test: anonymous check of a known-available name must return `available: true`. |
| F3 (no 401 challenge) | **Already built.** `McpOAuthChallengeFilter` returns 401 + `WWW-Authenticate: Bearer resource_metadata="…"`, and `OAuthProtectedResourceResource` serves RFC 9728 metadata. The audit saw 200+error because prod runs `MCP_OAUTH_CHALLENGE_ENABLED=false` — deliberately, because the challenge is **all-or-nothing**: enabling it 401s *every* unauthenticated call, killing anonymous tools and URL-only connectors. The real F3 work is the middle path (see A.3). |
| F7 (agent card gaps) | Half-true. `Skill` model has `tags`/`examples` fields — no agent populates them. Card already has absolute URL, capabilities, docs URL (fixed 2026-07). Missing: `securitySchemes`, deploy/mail skills (deploy tools exist only on the MCP side, no A2A agent), token-acquisition hint. |
| F4 (scopes) | Correct. `scopes_supported` is `openid profile email`. Blocked on Backend B.2 defining `osir:*` scopes — MCP side maps tools to scopes once they exist. |

Also relevant: `docs/TODO.md` already tracks the quarkus-mcp-server 1.11 → 2.x upgrade (stateless `2026-07-28` protocol). That upgrade removes the session-restart failure class and should land early — it makes A.3 and the eval numbers cleaner.

## Workstream plan

### A.0 Tool inventory — first, ~1 day
`docs/agent-readiness/TOOL-INVENTORY.md`, generated from the `@Tool` annotations + A2A agent registry, then hand-annotated: auth y/n, billable/destructive (staged), prerequisites, buyer, misleading-description flags. No latency column until telemetry (A.7) exists — mark "unknown" rather than guess.

### A.1 Eval harness — the metric, start same week
`evals/agent-tasks/` — ~20 YAML tasks + a runner (Python, pluggable model backends via each vendor's API, MCP client over Streamable HTTP).
- **Phase 1 (no sandbox needed):** read-only + stage-but-never-confirm tasks against prod. Gives a baseline for search/pricing/DNS/discovery chains immediately.
- **Phase 2 (blocked on Backend delivering OT&E + Stripe test env):** full register/pay/deploy tasks, autonomous-onboarding variants.
- CI: nightly smoke subset, weekly full run, JSON results committed to `evals/results/` for the trend.
Every later change (A.2/A.3/A.5/A.6) records its delta against this.

### A.3 Auth behaviour — the key design decision
Current state is a config either/or (challenge on = OAuth works, anonymous dies; off = anonymous works, no auto-discovery). Proposed resolution, replacing the flag:
1. Filter reads the JSON-RPC body: `initialize`, `tools/list`, and `tools/call` on anonymous-safe tools pass without auth; `tools/call` on auth-required tools without a Bearer → 401 + challenge + JSON-RPC error body naming `loginWithDevice` / OAuth as resolutions. Both buyers work on one endpoint, no env flag.
2. Anonymous-safe audit (from A.0): availability, suggestions, catalog, pricing, TLD lists — verify none falsely demand auth. **F1 is confirmed MCP-side** (see table above) and is the first concrete fix in this bucket; ship it ahead of the rest of A.3 — it's self-contained and unblocks anonymous agents immediately. Sweep every service in `common` for the same `isAuthenticated()` short-circuit pattern on read-only tools.
3. Scope enforcement: annotate each tool with its `osir:*` scope; enforce from the token once B.2 publishes scopes. Ship the mapping in the inventory now.
4. `idempotencyKey` argument on mutating tools, passed through as `Idempotency-Key` header — backend B.4 does the dedup; MCP just plumbs it.
5. Transparent refresh already exists (session churn fix, 2026-07); re-verify against B.2's new lifetimes.
6. **Done (2026-09-01):** Keycloak now enforces S256 PKCE on all public clients including the device grant (B.2 policy — kept, not relaxed). MCP side updated: `Pkce` util, PKCE params on `requestDeviceCode`/`pollDeviceToken`, verifier stored per device code (survives Claude.ai session churn; restart mid-login yields a clear "start a new login"). Downstream: eval runner (A.1), scripts 01/02 (A.8), and any DCR'd client must send PKCE — bake it into the harness's auth helper once, and say so in the agent guide (C.1) and `osir-connect` skill (A.6).

### A.4 Card + metadata — small, mechanical
- Populate `tags`/`examples` per skill at agent construction (data lives next to each agent); add deploy + mail skills (thin A2A agents delegating to existing MCP services); add `securitySchemes` → OAuth metadata URLs; `documentationUrl` → agent guide once C.1 publishes it.
- MCP `initialize.instructions`: 10–15 line orientation (what OSIR sells, both onboarding paths, staged-confirmation rule, pricing source). Check 1.11 support; if only in 2.x, that's another reason to do the upgrade first.
- `server.json` + `SUBMISSION-CHECKLIST.md` — static files, an afternoon.

### A.5 Description/return-shape pass — after A.0, before eval phase 2
Rewrite weakest descriptions (purpose, cost, prerequisites, what-next). Normalise price → `{amount, currency, taxIncluded, validUntil}` and error → `{code, message, resolution}` at the MCP model layer; where the backend shape differs, adapt in `common` models and file the backend fix under B.5. Re-run eval, record delta.

### A.6 Skills family — new repo `osir-skills/`
8 skills per the brief. Derive each from the existing GUIDE.md walkthroughs + MCP-TOOL-EXAMPLES.md rather than writing from scratch; current single `SKILL.md`/`claw.json` stays for OpenClaw compat and gets a pointer. Each skill = frontmatter + instructions + one worked transcript. Eval runs with/without skill loaded → keep only skills with a positive delta.

### A.7 Telemetry — MCP-side events, backend sink
A2A already has TaskMetrics/AuditLogger/correlation IDs. New: per-MCP-session event emission (client info from `initialize`, auth mode, tool sequence + timing, staged→confirmed, terminal outcome) posted to B.6's endpoint; buffer + drop on backend unavailability, never block a tool call. Dashboard is a backend/Grafana concern — we emit, they aggregate.

### A.8 E2E scripts — `scripts/agent-e2e/`
`01` (device flow, exists as manual walkthrough — script it), `04` (discovery curls — can be written today, becomes the F3/F7 acceptance check). `02`/`03` blocked on B.2 DCR + B.3 createAccount/verifyAccount + sandbox — write them against the agreed API shapes so they become the backend's acceptance tests too.

### A.2 Composite tools — deliberately last, eval-gated
No code now. After eval baseline + A.5 pass: list the worst chains, apply the a→b→c ladder (better descriptions → prerequisite auto-handling → composite). Note: `registerDomain` auto-initialising the DNS zone is Backend B.5 — that alone may kill the top chain-failure. Evaluate existing A2A `orchestrate`/`plan_workflow` skills before proposing `launchSite`. Each proposal = one page with eval evidence.

## Backend handoff status (2026-09-01, backend v2.11.0 — see HANDOFF-from-backend.md)

Done in this repo:
- **§1 F1 fixed**: availability (MCP tool + chat path + bulk) answers anonymously via the LIVE `GET /v1/public/catalog/domains/{d}/availability`; backend errors pass through as honest tool errors (`ToolErrors` — carries `code`/`message`/`resolution`), never a fabricated `available:false`. Regression tests in `DomainServiceAvailabilityTest`.
- **§2 getAuthStatus fixed**: an expired Bearer no longer short-circuits to `authenticated:false` — falls through to sessionKey/connection like every other tool (mirrors `resolveToken` precedence).
- **§3**: refresh-rotation bug fixed — `refreshSession` now updates every alias of a SessionAuth (osk_ key + connection id), required with `refreshTokenMaxReuse=2`. New refresh token already stored after each refresh. `offline_access` skipped: our session ceiling (8 h) is below Keycloak's 12 h SSO idle — revisit only if the ceiling is raised.
- **§4a**: `createAccount` + `verifyAccount` tools shipped (`AccountOnboardingMCPServer`, anonymous, audited; terms acceptance enforced; agent metadata for audit). Tool count 105.
- **§4c**: `registerDomain` gained optional `initializeDnsZone` (backend auto-inits by default); descriptions of `registerDomain` and `initializeDnsZone` updated so the zone step is no longer presented as mandatory.
- **§7-lite**: device login requests all eight `osir:*` scopes (verified accepted by live Keycloak), so tokens keep working when the backend starts enforcing scopes.

2026-09-02 — backend's next release went live; the deferred wave shipped:
- **§4b** `getHostingBundle` (anonymous, CatalogMCPServer) — the business-bet tool; description tells the model to offer hosting once after an availability/registration result. Verified against the live endpoint; payload passed through verbatim (typed only at the top level). Tool count 106.
- **§4d** `createPaymentSession` result now carries `checkoutUrl` + `expiresAt` + `pollTool`/`pollEndpoint`; description explains the hand-URL-to-human-then-poll loop.
- **§5** telemetry: `com.osir.mcp.telemetry` (client + buffering service + event builder) hooked into `McpAuditInterceptor`, which covers all 16 MCP server classes. Batches ≤200 every 30 s, drops on overflow/failure, never blocks a tool call. Funnel stages mapped by tool (authenticated/quoted/staged/confirmed/deployed; "registered" needs a PendingActionStore hook — TODO). **Inactive until `OSIR_TELEMETRY_API_KEY` (api-user role, issued for the MCP service) is configured; the key is sent as `X-API-Key`, not Bearer (handoff §5).**

Still open (docs/TODO.md): Idempotency-Key pass-through (backend ledger), per-tool scope declarations (with A.0 inventory), telemetry "registered" stage.

## Quality-score pass + MCP 2.x upgrade (2026-09-02)

Directory scorecard feedback (score 41) worked in full:
1. **Tool annotations** - all 105 tools carry `@Tool.Annotations` (title, readOnlyHint, destructiveHint, idempotentHint, openWorldHint=false). Deletes/unlock/OS-rebuild/executeConfirmedAction are destructiveHint=true; reads are readOnly+idempotent.
2. **Parameter descriptions** - every non-session parameter described (enums, formats, ranges: DNS types, paymentTerm values, +CC.number phones, ISO country codes, years 1-10).
3. **Output schemas** - `structuredContent=true` on the 11 most-chained tools (availability, pricing, quotes, bundle, balance, auth/device status, onboarding) + compatibility-mode so legacy clients still get text. Known gap: `@JsonPropertyDescription` on nested POJOs (RegistrantInfo, Contact) does not reach the generated schema in quarkus-mcp 2.0.0; the parameter-level descriptions carry the formats instead.
4. **Server instructions** - `server-info.instructions` ships the 5-rule orientation in every initialize response.
5. **Description length** - listCategorizedTlds 2002→373 chars, osirSiteDesignBrief 1513→369; zero em dashes remain in any tool metadata.
6. **Prompts** - all @Prompt methods have descriptions.
7. **Resources** - `osir://catalog/tlds` and `osir://catalog/products` (anonymous JSON, ~15 min cache).
8. **Near-duplicates** - disambiguation wording added (keyword pair, transferDomain vs initiateTransfer, suggestAlternatives marked legacy).
9. **Version signal** - serverInfo now explicit: name OSIR, title, version 2.2.0 (bump on every tool change!), websiteUrl, icon (osir.com/og-image.png 400x400).

**MCP 2.x upgrade**: quarkus-mcp-server 1.11.0 → 2.0.0 (see docs/TODO.md "MCP transport" for the endpoint-layout compat notes). Stateless auto-init verified; all 218 mcp-server tests green; wire-level smoke confirmed annotations/schemas/resources/icons/instructions.

## Sequencing (matches the brief's cross-project steps)

1. **Now:** F1 fix (MCP-side, self-contained) · A.0 inventory · A.1 phase-1 harness + baseline · MCP 2.x upgrade spike · A.8 `04-discovery.sh`
2. **Next:** A.3 body-aware challenge filter · A.4 card/instructions/server.json · A.5 description pass · re-baseline
3. **Then:** A.6 skills repo · A.7 telemetry emission · A.8 scripts 01–03 (03 when sandbox lands)
4. **Last:** A.2 composite decisions from eval evidence

## Dependencies on Backend (blocking, in order of pain)
- OT&E + Stripe-test sandbox → eval phase 2, scripts 02/03
- `osir:*` scope definitions in Keycloak (B.2) → A.3 scope enforcement
- DCR policy decision (B.2/F2) → autonomous onboarding path at all
- B.6 telemetry ingest endpoint → A.7
- `createAccount`/`verifyAccount` API shapes (B.3) → scripts 02/03 authoring

## Open questions for Osir
1. A.3 middle-path design (body-inspecting challenge filter) replaces the `MCP_OAUTH_CHALLENGE_ENABLED` flag — OK to drop the flag, or keep it as escape hatch one more release?
2. Eval runner model access: which API keys/accounts exist for GPT + Gemini runs?
3. `osir-skills/` — separate public repo under Osir-Inc, same commit-identity policy?
4. MCP 2.x upgrade is a compat spike with unknown size (TODO.md) — approve doing it first, before A.3, since both touch the transport layer?
