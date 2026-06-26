# OSIR MCP & A2A Server — Progress

## Public Open-Source Release (2026-06-25)

Published to **https://github.com/Osir-Inc/mcp-a2a** (branch `main`), mirrored to the
team's private Gitea (`main`). History is a single squashed orphan commit, author
`Armand <noreply@osir.com>`, **no Claude/Anthropic references** (commit policy — see CLAUDE.md).

Release prep done:
- Secret/leak audit: clean (no keys/tokens/certs). Scrubbed internal Ollama IP →
  `localhost:11434`, CI registry → `registry.example.com`. Untracked `.claude/` (gitignored).
- Apache-2.0 `LICENSE`; usage-focused `README.md` (custom-connector + `What is MCP?`).
- All non-README docs moved into `docs/` to keep the repo root clean.
- `master` and stale feature branches deleted on both remotes; `main` is default.

Hardening + fixes (2026-06-26, commit after release):
- Removed rogue `CorsFilter` (`*` + credentials, overrode CORS allow-list); expose
  `Mcp-Session-Id` via Quarkus config.
- JWT issuer now matched **exactly** vs `${KEYCLOAK_URL}/realms/${realm}` (was substring).
- `CatalogService` caches only **successful** responses (was caching failures for the TTL).
- Chat path fixed: `loginWithDevice`/`checkDeviceLoginStatus` dispatch + inverted availability result.
- Repaired stale `VpsServiceTest`/`DnsServiceTest` (didn't compile on committed `main`).
  **Test count is now 372** (was documented as 351).

Known but NOT yet done (from the review — candidates for next session):
- A2A token isolation: request-scoped `AuthContext` is read on `executor.submit()` worker
  threads (no context propagation) — pass the token explicitly instead. (High)
- A2A: client-supplied task IDs have no ownership check (cross-user task access). (High)
- `/api/chat/*` MCP chat surface is unauthenticated. (High)
- Ollama `HttpClient` has no timeouts; webhook push runs blocking `Thread.sleep` retries on
  the request thread + no SSRF allowlist; orchestrator marks partial success as FAILED.
- No GitHub Actions CI (only `.gitea/workflows` exists) — build can go red unnoticed.

## Complete — Production Ready

### Platform (Gradle 8.14.1, Quarkus 3.34.2, 351 tests)
- [x] MCP: 71 tools, 10 prompts, SSE + Streamable HTTP transports
- [x] A2A: 7 agents (Domain, DNS, VPS, Billing, Contact, Account, Orchestrator), 50+ skills
- [x] 351 tests (129 common + 138 mcp-server + 84 a2a-server), 0 failures

### API & Endpoints
- [x] All backend v2 API endpoints fixed (17+ paths migrated)
- [x] Token refresh via KeyCloak (expired + refreshToken = auto-refresh)
- [x] Token expiry warnings in response metadata (< 5 min threshold)
- [x] SSE streaming at /a2a/stream with full parity to /a2a
- [x] OpenAPI/Swagger at /q/swagger-ui

### Security
- [x] JWT local claims validation (expiry + issuer)
- [x] Secure session IDs (SecureRandom), volatile currentSessionId
- [x] Request-scoped AuthContext for multi-tenant token isolation
- [x] Per-user rate limiting (SHA-256 hash, 10/user, 50 global)
- [x] CORS locked down (configurable origins, dev mode permissive)
- [x] Optional HMAC-SHA256 request signing
- [x] CSRF mitigation (@Consumes JSON)
- [x] Error message sanitization (no internal details leaked)
- [x] Max body size (256K A2A, 1M MCP)
- [x] Input validation, null safety

### Operations
- [x] Prometheus metrics (TaskMetrics: 8 counters + 1 timer)
- [x] Structured audit logging (AuditLogger with task lifecycle events)
- [x] Correlation IDs (X-Request-ID via MDC, propagated to backend)
- [x] Startup validation (backend + KeyCloak reachability check)
- [x] Graceful degradation (BackendErrorHandler for user-friendly errors)
- [x] Production JSON logging (%prod profile)
- [x] Docker HEALTHCHECK in Dockerfiles
- [x] docker-compose healthcheck directives

### Architecture
- [x] BaseSpecialistAgent + DomainUtils (shared code, no duplication)
- [x] Bounded thread pools with @PreDestroy (A2A: 2-20, SSE: 2-10, Orchestrator: 4)
- [x] Orchestrator: max 15 steps, 15s per-step timeout
- [x] Task execution timeout: 30s
- [x] Persistent task store (JPA + H2, PostgreSQL-ready)
- [x] Push notifications (webhook, exponential backoff, 3 retries)
- [x] Caching (CatalogService + domain pricing, 15min TTL)
- [x] Cache + DB cleanup (terminal 1h, stuck 24h)

### Deployment & Docs
- [x] build-and-deploy.bat, docker-compose.yml, .env.example
- [x] CI/CD (Gitea Actions: build+test on PR, Docker push on master)
- [x] GUIDE.md: MCP walkthrough (6 steps) + A2A walkthrough (10 steps)
- [x] DEPLOYMENT.md: production checklist (8 steps + nginx + troubleshooting)
- [x] A2A-API-REFERENCE.md: complete protocol spec
- [x] SKILL.md + claw.json (OpenClaw)
- [x] Gradle wrapper SHA-256 checksum verification
- [x] All 12 env vars documented in .env.example
- [x] Config audit: no hardcoded IPs in code, aligned client-ids, no obsolete config

### LLM-Compatibility Fixes (MCP)
- [x] `@ToolArg(required = false)` applied to all optional params across all 11 MCP server files (40+ parameters) — prevents "Missing required argument" errors for fresh LLMs
- [x] `listCategorizedTlds` registry filter: empty string `""` now treated as no filter (was causing zero-candidate results)
- [x] `listCategorizedTlds` debug logging: logs total extensions, extensions with metadata, final count after filters
- [x] `listCategorizedTlds` controlled vocabulary updated: added startup/infrastructure/journal/banking categories, startup audience, decimal string price note
- [x] `executeConfirmedAction` now reflects inner result `success` field instead of hardcoding `true`; audit log includes `summary=` field
- [x] `checkKeywordAvailability` / `checkKeywordAvailabilitySummary`: replaced `throw RuntimeException` with `return McpError` (was crashing the tool call)
- [x] `ContactService.createContact` / `updateContact`: `WebApplicationException` body extracted and returned (real validation errors now surfaced)
- [x] `getContactsForDomain`: parameter renamed `domainId` → `domain` to match backend path `/v2/domains/{domain}/contacts`

### LLM-Compatibility Fixes (A2A)
- [x] `get_domain_contacts` prompt: "domain ID" → "domain name (e.g., 'example.com')" in ContactSpecialistAgent
- [x] `BaseSpecialistAgent`: added `meta()`, `metaInt()`, `metaDouble()` helpers for reading structured params from task metadata
- [x] `DnsSpecialistAgent`: implemented `create_dns_record` (metadata: domain/name/type/content + optional ttl/priority), `update_dns_record` (metadata: domain/recordId + optional fields), `get_dns_record` (metadata: domain/recordId) — were all stubs before
- [x] `BillingSpecialistAgent`: implemented `get_invoice`, `pay_invoice`, `preview_fees`, `create_payment` using metadata params — were all INPUT_REQUIRED stubs before
- [x] `VpsSpecialistAgent`: implemented `order_vps` (metadata: packageId/hostname/paymentTerm + optional OS), `get_vps_details`, `delete_vps`, `vps_panel_login` (all via instanceId) — were all stubs before
- [x] `ContactSpecialistAgent`: implemented `create_contact` (8 required + 3 optional metadata fields), `get_contact`, `update_contact`, `delete_contact`, `get_domain_contacts` (domain from metadata or text) — were all stubs before
- [x] `DomainSpecialistAgent`: removed orphan `manage_domain` from SKILL_IDS (was causing "Unknown skill" error)
