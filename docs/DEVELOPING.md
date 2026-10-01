# Developing

Orientation for anyone working on this repository: how it is laid out, how to build and run it, and
the design decisions worth knowing before you change something.

## Project Overview

OSIR domain registrar AI platform with two servers:
- **MCP Server** (Quarkus, port 8081) — 106 tools + 11 prompts via Model Context Protocol
- **A2A Server** (Quarkus, port 8082) — 9 agents / 89 skills via Google Agent-to-Agent protocol

**Note:** CLI tools moved to `../com.osir.cli`.

## Project Structure

```
com.osir.agent/
├── common/          # Java library: 12 services, 9 REST clients, ~174 models
│   └── security/    # DestructiveOpRateLimiter — shared by BOTH transports' confirmation gates
├── mcp-server/      # Quarkus: 15 *MCPServer classes (tools + prompts), chat UI, health
├── a2a-server/      # Quarkus: A2A protocol, 9 agents, audit logging
│   ├── protocol/    # AgentCard, A2ATask, Message/Part, Artifact, JSON-RPC, TaskStore
│   ├── agents/      # BaseSpecialistAgent + 8 specialists + OrchestratorAgent
│   ├── security/    # ConfirmationGate (staged confirmations for billable/destructive skills)
│   └── resources/   # A2AResource, A2ASseResource, AgentCardResource, AuditLogger, RateLimitFilter
├── settings.gradle  # include 'common', 'mcp-server', 'a2a-server'
└── build.gradle     # Parent-only config
```

## Commands

```bash
./gradlew build                    # Build all modules (544 tests)
./gradlew quarkusDev               # MCP server dev mode (port 8081)
./gradlew :a2a-server:quarkusDev   # A2A server dev mode (port 8082)
./gradlew test                     # Run all tests
build-and-deploy.bat               # Build Docker images and push
build-and-deploy.bat --no-push     # Build without pushing
```

### Docker
```bash
docker-compose up -d               # Start both servers
docker-compose logs -f             # View logs
```

## Architecture

### MCP Server
- 15 `*MCPServer.java` classes with `@Tool` and `@Prompt` annotations at `/mcp` (SSE)
- 106 tools, counted per `*MCPServer` class: domain+auth+suggestions (24), VPS (15), mail (11), deployment (10), billing (10), DNS (6), contacts (6), transfer (5), catalog (5), host (4), account (2) + onboarding (2), audit (3), website design (2), confirmation (1) — canonical list in `MCP-TOOL-EXAMPLES.md`. Verify with `tools/list`, not by grepping `@Tool(` — `DomainRegistrarMCPServer` carries a commented-out `bulkCheckDomains` annotation that inflates a naive grep by one.
- 11 prompts: getting_started, vps_setup_guide, dns_setup_guide, billing_overview, domain_management_guide, hosting_comparison, troubleshooting, security_best_practices (PromptsMCPServer); domain_registration_guide, domain_transfer_checklist (DomainRegistrarMCPServer); website_designer (WebsiteDesignMCPServer)
- Website design: the calling LLM designs; `osirSiteDesignBrief` returns the prompt, `osirSitePublish` gates + zips + deploys. Open items in [TODO.md](TODO.md)
- Caching: CatalogService + domain pricing (15min TTL via `@CacheResult`)
- **Confirmation gate** (`security/PendingActionStore`): 20 tools that spend money or destroy data
  return an `actionId` + summary instead of running; `executeConfirmedAction` runs the frozen callable
  once, for the same principal, within 5 minutes, rate limited per `DestructiveOpRateLimiter` bucket.
  `security/ConfirmationGateInvariantTest` fails the build if a tool with `destructiveHint = true`
  does not stage, or if the staged set drifts from its `GATED` list — add new billable tools there.
- **Audit trail**: every tool call and every confirmed action logs on `com.osir.mcp.audit`, which also
  goes to its own rotating file (`AUDIT_LOG_PATH`, default `data/audit.log`; mount it in production).
  The Quarkus handler is named `audit-trail` on purpose — a handler literally named `audit` fails to
  attach on Quarkus 3.34 with "linked to a category but not configured".
- **Crash reporting** (`telemetry/ErrorReporting`, off unless `SENTRY_DSN` is set): GlitchTip over the
  Sentry protocol. Captures come from `telemetry/BackendErrorCaptureFilter`, a `ClientResponseFilter`
  on the global REST-client providers — that is the only place that sees a backend 5xx a tool
  swallowed into a `success:false` result, since `ToolErrors` covers barely any of the 106 tools.
  `ToolErrors` still reports transport and parse failures, which never reach a response filter.
  A 4xx is never reported: it is the user or a business refusal, not an incident.
  **Adding a credential shape:** put the regex in `ErrorReporting.CREDENTIALS` and a case in
  `ErrorReportingTest` — `beforeSend` is the only thing between a stack frame and a live token, and
  the same shapes are masked backend-side in `com.osir.logging.SecretMaskingLogFilter`. Events are
  throttled to one per route per minute because the monthly quota is shared with the backend and the
  panel, and `route()` strips ids, uuids and domains so customer data stays out of the tracker.

### A2A Server
- 9 agents, 89 skills: Domain (27), VPS (16), Billing (11), Mail (8), DNS (7), Contact (7), Account (6),
  Deploy (5), Orchestrator (2)
- **Confirmation gate** (`security/ConfirmationGate`): billable and destructive skills are STAGED, not
  run — the agent answers `input-required` with a summary and an actionId, and the caller confirms by
  echoing that id on the SAME task. Parameters are frozen at stage time (as data on the task, so a
  restart does not forget them), single-use, 5-minute expiry, caller matched on the JWT subject, rate
  limited via the shared `DestructiveOpRateLimiter`. A task with a live staged action is pinned to the
  agent that staged it, because a continuation is otherwise re-scored from scratch.
  **It is not an authorization control** — see `A2A-CONFIRMATION-GATE-SPEC.md` §2 and §6.
- All extend `BaseSpecialistAgent` (shared scoring via `DomainUtils`, error handling)
- Scoring-based routing via `AgentRegistry` (single-pass, explicit `skill`/`agent` params get 1.0)
- `OrchestratorAgent`: rule-based task decomposition, max 15 steps, 15s per-step timeout
- `AuditLogger`: structured TASK_SUBMITTED/COMPLETED/FAILED events with duration
- `RateLimitFilter`: per-user SHA-256 (10 concurrent) + global (50), proper acquire/release
- `AuthContext` (@RequestScoped): per-request token override, JWT claims validation (expiry + issuer)
- `CorrelationFilter`: X-Request-ID via MDC for log tracing
- `RequestSigningFilter`: optional HMAC-SHA256 verification for agent-to-agent calls
- Bounded thread pools: A2AResource (2-20), A2ASseResource (2-10), Orchestrator (4), all with @PreDestroy
- Task execution timeout: 30s (both REST and SSE endpoints)
- SSE streaming: `POST /a2a/stream` via Mutiny Multi (full parity with REST endpoint)
- Persistence: JPA TaskStore with H2 file-based (PostgreSQL via env vars), in-memory cache
- Push notifications: webhook callbacks with exponential backoff (3 retries)
- Scheduled cleanup: terminal tasks after 1h, stuck tasks after 24h (cache + DB)
- Max body size: 256K

### Shared
- `common/` module: 12 services, 9 REST clients, shared by both servers
- Backend URL: `${OSIR_BACKEND_URL:https://be.osir.com}`
- KeyCloak: `${KEYCLOAK_URL:https://auth.osir.com}`, realm `osir`
- Ollama: `${OLLAMA_URL:http://localhost:11434}`, model `qwen2.5:14b`

### Backend API Versions
- Domain: `/v2/domains/{domain}/...` (available, info, register, renew, lock, unlock, autorenew, privacy, nameservers)
- Transfers: `/v2/transfer/...`
- Hosts: `/v2/hosts/...`
- DNS: `/dns/domains/{domain}/records/...`
- Billing: `/v1/billing/invoices/...`, `/v1/payment/...`
- VPS: `/v1/hosting/vps/...`
- Catalog: `/v1/public/catalog/...`
- Contacts: `/v1/contacts/...`
- Audit: `/v1/audit/...`

## Configuration (Environment Variables)

| Variable | Default | Description |
|----------|---------|-------------|
| `OSIR_BACKEND_URL` | `https://be.osir.com` | Backend API |
| `KEYCLOAK_URL` | `https://auth.osir.com` | KeyCloak auth |
| `KEYCLOAK_REALM` | `osir` | KeyCloak realm |
| `KEYCLOAK_CLIENT_ID` | `osir-cli` | OAuth client |
| `OLLAMA_URL` | `http://localhost:11434` | LLM service |
| `CORS_ORIGINS` | `https://osir.com,...` | Allowed origins |
| `A2A_PUBLIC_URL` | _(request-derived)_ | Public HTTPS base for the agent card `url` (set behind a TLS proxy) |
| `A2A_DOCUMENTATION_URL` | `github.com/Osir-Inc/mcp-a2a` | Agent card `documentationUrl` |

## Key Files

- `GUIDE.md` — Full usage guide (MCP + A2A + deployment)
- `VPS-OS-BUILD.md` — VPS OS install: the two-key `listVpsOsTemplates`, SSH keys, reinstall, OSIR APP
  DEPLOY flow. **Read before touching the VPS tools** — VirtFusion orders and builds separately, so
  `orderVps` without `operatingSystemId` yields a server with no OS. Needs backend v2.9.1+.
- `A2A-ARCHITECTURE.md` — A2A architecture design document
- `A2A-CONFIRMATION-GATE-SPEC.md` — the staged-confirmation design, what it does NOT protect, and the
  three layers still missing (scopes, spend cap, out-of-band approval)
- `SKILL.md` — OpenClaw compatibility manifest

## Remaining Work

Tracked in [TODO.md](TODO.md). The two that shape the design:

- **A2A gate Layers B–D** — token scopes, a backend spend cap, and out-of-band approval. Layer A (the
  staged confirmation) is in, and it does not stop an unattended caller by itself.
- **Per-tool `osir:*` scopes** — blocked on Keycloak scope definitions.

Nice-to-haves:
- `AuthContext.refreshedToken` is wired but nothing sets it yet (needs token refresh flow)
- Docker compose health check directives
