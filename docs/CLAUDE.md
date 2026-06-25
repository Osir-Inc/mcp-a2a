# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

OSIR domain registrar AI platform with two servers:
- **MCP Server** (Quarkus, port 8081) — 71 tools + 10 prompts via Model Context Protocol
- **A2A Server** (Quarkus, port 8082) — 7 specialist agents via Google Agent-to-Agent protocol

**Note:** CLI tools moved to `../com.osir.cli`.

## Public Release / GitHub

This is the public, open-source edition of the platform.

- **GitHub (public):** https://github.com/Osir-Inc/mcp-a2a — default branch `main`
- An internal mirror is kept in sync on the team's private Gitea (branch `main`).
- The public history is a single squashed commit (orphan branch), intentionally with **no prior development history**.

**Commit policy for this repo:** commit messages must contain **no Claude/Anthropic references** — do not add `Co-Authored-By: Claude` (or any AI) trailers, and keep messages plain. This overrides any default attribution behavior.

## Project Structure

```
com.osir.agent/
├── common/          # Java library: 12 services, 9 REST clients, ~174 models
├── mcp-server/      # Quarkus: 11 MCP servers (10 tools + 1 prompts), chat UI, health
├── a2a-server/      # Quarkus: A2A protocol, 7 agents, audit logging
│   ├── protocol/    # AgentCard, A2ATask, Message/Part, Artifact, JSON-RPC, TaskStore
│   ├── agents/      # BaseSpecialistAgent + 6 specialists + OrchestratorAgent
│   └── resources/   # A2AResource, A2ASseResource, AgentCardResource, AuditLogger, RateLimitFilter
├── settings.gradle  # include 'common', 'mcp-server', 'a2a-server'
└── build.gradle     # Parent-only config
```

## Commands

```bash
./gradlew build                    # Build all modules (351 tests)
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
- 11 `*MCPServer.java` classes with `@Tool` and `@Prompt` annotations at `/mcp` (SSE)
- 71 tools: auth (5), domain (12), suggestions (7), DNS (5), VPS (10), billing (9), contacts (6), transfer (5), host (4), audit (3), catalog (3), account (2)
- 10 prompts: getting_started, domain_registration_guide, domain_transfer_checklist, vps_setup_guide, dns_setup_guide, billing_overview, domain_management_guide, hosting_comparison, troubleshooting, security_best_practices
- Caching: CatalogService + domain pricing (15min TTL via `@CacheResult`)

### A2A Server
- 7 agents: Domain (13 skills), DNS (5), VPS (8), Billing (9), Contact (6), Account (6), Orchestrator (2)
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

## Key Files

- `GUIDE.md` — Full usage guide (MCP + A2A + deployment)
- `A2A-ARCHITECTURE.md` — A2A architecture design document
- `PROGRESS.md` — Completed work + remaining TODO items
- `SKILL.md` — OpenClaw compatibility manifest

## Remaining Work

See `PROGRESS.md`. All critical items are complete. Nice-to-haves:
- Unit tests for DNS/VPS/Billing/Contact/Account agents (currently only Domain agent has unit tests)
- Unit tests for PromptsMCPServer
- `AuthContext.refreshedToken` is wired but nothing sets it yet (needs token refresh flow)
- Docker compose health check directives
