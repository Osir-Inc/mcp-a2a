# OSIR MCP & A2A Server — Usage Guide

## Overview

This project provides two complementary AI integration servers for the OSIR domain registrar:

| Server | Port | Protocol | Purpose |
|--------|------|----------|---------|
| **MCP Server** | 8081 | Model Context Protocol (SSE) | 71 individual tools for AI assistants |
| **A2A Server** | 8082 | Google Agent-to-Agent (JSON-RPC) | Specialist agents for task-level orchestration |

**MCP** is for single-agent use: Claude, ChatGPT, or any MCP-compatible client calls individual tools like `checkDomainAvailability` or `registerDomain`.

**A2A** is for multi-agent orchestration: agents delegate complex tasks (e.g., "set up a client with 5 domains") to specialist agents that coordinate the work.

---

## Part 1: Using the MCP Server with Claude

### Quick Start

```bash
# Build
./gradlew build

# Run in dev mode
./gradlew :mcp-server:quarkusDev

# MCP endpoint available at http://localhost:8081/mcp
```

### Connecting Claude Desktop

Add this to your Claude Desktop configuration file:

**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "osir": {
      "url": "http://localhost:8081/mcp/sse"
    }
  }
}
```

**Streamable HTTP** (newer clients that support it):
```json
{
  "mcpServers": {
    "osir": {
      "url": "http://localhost:8081/mcp/http"
    }
  }
}
```

Restart Claude Desktop. You'll see OSIR tools appear in the tools menu.

### Connecting Claude Code (CLI)

Add this to your project's `.mcp.json` or `~/.claude/settings.json`:

```json
{
  "mcpServers": {
    "osir": {
      "type": "sse",
      "url": "http://localhost:8081/mcp/sse"
    }
  }
}
```

### Authentication

Most tools require authentication. Use one of these methods:

**Option A: Device Flow (recommended)**
1. Call `loginWithDevice` — returns a URL and code
2. Open the URL in your browser, enter the code
3. Authenticate with KeyCloak (supports MFA/SSO)
4. Call `checkDeviceLoginStatus` with the device code to complete login

**Option B: Username/Password**
1. Call `authenticateUser` with username and password

### Available Tools (71 total)

#### Authentication (5)
| Tool | Description |
|------|-------------|
| `loginWithDevice` | Start browser-based OAuth login (RFC 8628) |
| `checkDeviceLoginStatus` | Poll for login completion |
| `authenticateUser` | Username/password login |
| `getAuthStatus` | Check if authenticated |
| `logout` | End session |

#### Domain Management (12)
| Tool | Description |
|------|-------------|
| `checkDomainAvailability` | Check if a domain is available |
| `registerDomain` | Register a new domain |
| `getDomainInfo` | Get domain details (expiry, nameservers, status) |
| `listUserDomains` | List all your domains |
| `updateNameservers` | Change nameservers |
| `renewDomain` | Renew for 1-10 years |
| `lockDomain` | Enable registrar lock |
| `unlockDomain` | Remove registrar lock |
| `updateDomainAutoRenew` | Enable/disable auto-renewal |
| `updateDomainPrivacy` | Enable/disable WHOIS privacy |
| `transferDomain` | Transfer from another registrar |
| `validateDomainName` | Check domain name format |

#### Domain Suggestions (7)
| Tool | Description |
|------|-------------|
| `generateDomainSuggestions` | AI-powered domain suggestions |
| `spinDomainWords` | Generate variations by word spinning |
| `addPrefixToDomain` | Generate with prefixes |
| `addSuffixToDomain` | Generate with suffixes |
| `bulkDomainSuggestions` | Bulk suggestions for multiple keywords |
| `checkKeywordAvailability` | Check keyword across TLDs |
| `checkKeywordAvailabilitySummary` | Availability summary stats |

#### DNS (5)
`listDnsRecords`, `createDnsRecord`, `updateDnsRecord`, `deleteDnsRecord`, `getDnsRecord`

#### VPS Hosting (10)
`listVpsPackages`, `listVpsLocations`, `getVpsPackageDetails`, `orderVps`, `listMyVpsInstances`, `getVpsInstanceDetails`, `deleteVpsInstance`, `changeVpsPaymentTerm`, `loginToVpsPanel`, `countMyVpsInstances`

#### Billing (9)
`getAccountBalance`, `listInvoices`, `getInvoiceDetails`, `payInvoice`, `getInvoiceStatistics`, `createPaymentSession`, `getPaymentTransactions`, `previewPaymentFees`, `getDomainPricing`

#### Contacts (6), Transfers (5), Hosts (4), Audit (3), Catalog (3), Account (2)

### Example Conversations with Claude

**Find and register a domain:**
> "I need a domain for my pizza restaurant in Tirana. Check if pizzashqip.al is available, and if not, suggest alternatives."

Claude will:
1. Call `checkDomainAvailability("pizzashqip.al")`
2. If unavailable, call `generateDomainSuggestions("pizzashqip", "al,com")`
3. Present options with pricing

**Manage your portfolio:**
> "List all my domains and show which ones expire in the next 30 days."

Claude will:
1. Call `listUserDomains()`
2. Analyze expiration dates
3. Suggest renewals

**Set up DNS:**
> "Add an A record pointing example.al to 203.0.113.10 and a CNAME for www."

Claude will:
1. Call `createDnsRecord("example.al", "@", "A", "203.0.113.10", 3600, null)`
2. Call `createDnsRecord("example.al", "www", "CNAME", "example.al", 3600, null)`

---

## Part 2: Using the A2A Server

### Quick Start

```bash
# Run A2A server
./gradlew :a2a-server:quarkusDev

# Agent card discovery
curl http://localhost:8082/.well-known/agent.json

# List all specialist agents
curl http://localhost:8082/.well-known/agents
```

### Sending Tasks via JSON-RPC

**Basic task (intent-based routing):**
```bash
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tasks/send",
    "params": {
      "id": "task-001",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Check if example.com is available"}]
      }
    }
  }'
```

**Explicit skill routing (deterministic):**
```bash
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tasks/send",
    "params": {
      "id": "task-002",
      "skill": "check_availability",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "example.com"}]
      }
    }
  }'
```

**SSE streaming (real-time updates):**
```bash
curl -X POST http://localhost:8082/a2a/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tasks/sendSubscribe",
    "params": {
      "skill": "register_domain",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Register mycompany.com for 2 years"}]
      }
    }
  }'
```

This streams SSE events as the task progresses: `submitted` → `working` → `completed`.

### Task Lifecycle

```
submitted  →  working  →  completed
                       →  failed
                       →  input-required  →  (send more info)  →  working  →  ...
                       →  canceled
```

When a task returns `input-required`, send another `tasks/send` with the same task ID and the requested information as a new message.

### Retrieving and Canceling Tasks

```bash
# Get task status
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"4","method":"tasks/get","params":{"id":"task-001"}}'

# Cancel a task
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":"5","method":"tasks/cancel","params":{"id":"task-001"}}'
```

### Available Agents & Skills (50+ skills across 7 agents)

**Domain Agent** (`agent: "domain-agent"`)
`check_availability`, `register_domain`, `get_domain_info`, `list_domains`, `renew_domain`, `lock_domain`, `unlock_domain`, `suggest_domains`, `transfer_domain`, `enable_privacy`, `disable_privacy`, `enable_autorenew`, `disable_autorenew`

**DNS Agent** (`agent: "dns-agent"`)
`list_dns_records`, `create_dns_record`, `update_dns_record`, `delete_dns_record`, `get_dns_record`

**VPS Agent** (`agent: "vps-agent"`)
`list_vps_packages`, `list_vps_locations`, `order_vps`, `list_vps_instances`, `get_vps_details`, `delete_vps`, `vps_panel_login`, `get_catalog`

**Billing Agent** (`agent: "billing-agent"`)
`get_balance`, `list_invoices`, `get_invoice`, `pay_invoice`, `invoice_statistics`, `create_payment`, `get_transactions`, `preview_fees`, `get_domain_pricing`

**Contact Agent** (`agent: "contact-agent"`)
`list_contacts`, `get_contact`, `create_contact`, `update_contact`, `delete_contact`, `get_domain_contacts`

**Account Agent** (`agent: "account-agent"`)
`get_profile`, `get_account_summary`, `get_auth_status`, `get_audit_logs`, `get_domain_audit`, `get_recent_activity`

**Orchestrator** (`agent: "orchestrator"`)
`orchestrate`, `plan_workflow` — decomposes complex tasks across multiple agents (max 15 steps)

### Orchestrator Example

Send a complex multi-step request:
```bash
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "orch-1",
    "method": "tasks/send",
    "params": {
      "skill": "orchestrate",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Set up example.com with DNS and check my balance"}]
      }
    }
  }'
```

The orchestrator will: check domain availability, list DNS records, and retrieve account balance — returning all results in a single workflow.

### Multi-Turn Conversations

The A2A protocol supports multi-turn interactions. Example: transferring a domain.

**Turn 1:** Send transfer request without auth code
```json
{"message": {"role": "user", "parts": [{"type": "text", "text": "Transfer example.com"}]}}
```
Response: `status: "input-required"` — "Please provide the authorization/EPP code"

**Turn 2:** Send the same task ID with the auth code
```json
{"id": "same-task-id", "message": {"role": "user", "parts": [{"type": "text", "text": "Auth code is Xk9-mP2q"}]}}
```
Response: `status: "completed"` — Transfer initiated

### Token Refresh

Tokens are validated locally (expiry + issuer) before every task execution.

**When your token is near expiry** (< 5 minutes), the response includes a warning:
```json
{ "result": { "metadata": { "tokenExpiresIn": 120 } } }
```

**When your token is expired**, you get error code `-32003`:
```json
{ "error": { "code": -32003, "message": "Token expired. Provide a refreshToken..." } }
```

**Automatic refresh** — include your KeyCloak refresh token in the request:
```bash
curl -X POST http://localhost:8082/a2a \
  -H "Authorization: Bearer YOUR_EXPIRED_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tasks/send",
    "params": {
      "refreshToken": "YOUR_KEYCLOAK_REFRESH_TOKEN",
      "skill": "list_domains",
      "agent": "domain-agent",
      "message": { "role": "user", "parts": [{"type": "text", "text": "list"}] }
    }
  }'
```

If the refresh succeeds, the task executes with the new token and the response includes:
```json
{ "result": { "metadata": { "token": { "accessToken": "Bearer new-token", "refreshToken": "new-refresh" } } } }
```

Use the returned tokens for subsequent requests.

### Integrating A2A with Your Own Agent

Any A2A-compliant client can discover and use OSIR agents:

1. **Discover:** `GET http://your-osir-host:8082/.well-known/agent.json`
2. **Inspect skills:** Parse the `skills` array from the agent card
3. **Send tasks:** `POST http://your-osir-host:8082/a2a` with JSON-RPC 2.0
4. **Handle responses:** Check `status` field — `completed`, `failed`, or `input-required`
5. **Read artifacts:** Structured data in `artifacts[].parts[].data`

---

## Part 3: Deployment

### Build and Deploy

```batch
:: Build and push both images
build-and-deploy.bat

:: Build only MCP server
build-and-deploy.bat mcp

:: Build only A2A server
build-and-deploy.bat a2a

:: Build without pushing
build-and-deploy.bat --no-push
```

### Docker Compose

```bash
# Start both servers
docker-compose up -d

# With custom backend
OSIR_BACKEND_URL=http://localhost:8080 docker-compose up -d

# View logs
docker-compose logs -f
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OSIR_BACKEND_URL` | `https://be.osir.com` | Backend API URL |
| `KEYCLOAK_URL` | `https://auth.osir.com` | KeyCloak auth server |
| `KEYCLOAK_REALM` | `osir` | KeyCloak realm |
| `KEYCLOAK_CLIENT_ID` | `osir-cli` | OAuth client ID |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama LLM service |
| `CORS_ORIGINS` | `https://osir.com,...` | Allowed CORS origins |
| `A2A_RATE_LIMIT_GLOBAL` | `50` | Max concurrent A2A requests total |
| `A2A_RATE_LIMIT_PER_USER` | `10` | Max concurrent A2A requests per user |
| `A2A_SIGNING_SECRET` | *(empty)* | HMAC-SHA256 secret for request signing |
| `A2A_SIGNING_REQUIRED` | `false` | Reject unsigned A2A requests |

### Ports

| Service | Port | Endpoints |
|---------|------|-----------|
| MCP Server | 8081 | `/mcp/sse`, `/mcp/http`, `/q/health`, `/q/dev` |
| A2A Server | 8082 | `/a2a`, `/a2a/stream`, `/.well-known/agent.json`, `/q/swagger-ui`, `/q/health`, `/q/metrics` |

---

## Part 4: Architecture

```
┌─────────────────────────────┐
│   Claude / AI Assistants    │
└──────┬──────────┬───────────┘
       │ MCP      │ A2A
       │ (tools)  │ (tasks)
┌──────▼──────┐ ┌─▼──────────────────────────┐
│ MCP Server  │ │ A2A Server                  │
│ :8081       │ │ :8082                       │
│ 71 tools    │ │ Orchestrator                │
│ 10 prompts  │ │  ├─ Domain Agent (13 skills)│
│             │ │  ├─ DNS Agent (5 skills)    │
│             │ │  ├─ VPS Agent (8 skills)    │
│             │ │  ├─ Billing Agent (9 skills)│
│             │ │  ├─ Contact Agent (6 skills)│
│             │ │  └─ Account Agent (6 skills)│
└──────┬──────┘ └──┬─────────────────────────┘
       │           │
       └─────┬─────┘
             │
     ┌───────▼────────┐      ┌───────────────┐
     │    common/     │      │ Rate Limiter   │
     │ 12 Services    │      │ Audit Logger   │
     │  9 Clients     │      │ Auth Context   │
     │  Caching       │      │ Health Check   │
     └───────┬────────┘      └───────────────┘
             │
     ┌───────▼────────┐
     │ OSIR Backend   │
     │ be.osir.com    │
     │ 403 endpoints  │
     └────────────────┘
```

Both servers share the `common` module — same services, same REST clients, same models. The MCP server exposes them as 71 individual tools. The A2A server wraps them in 7 specialist agents with 50+ skills, coordinated by an orchestrator that handles multi-step workflows.

### Security & Operations
- **CORS:** restricted to configured origins (permissive in dev mode)
- **Rate limiting:** per-user SHA-256 (10 concurrent) + global (50)
- **Auth isolation:** request-scoped `AuthContext`, JWT claims validation (expiry + issuer)
- **Token refresh:** via KeyCloak when client provides refreshToken
- **Audit logging:** structured TASK_SUBMITTED/COMPLETED/FAILED events with duration
- **Correlation IDs:** X-Request-ID propagated through all layers (A2A to backend)
- **Task timeout:** 30s per agent, 15s per orchestrator step
- **Request signing:** optional HMAC-SHA256 verification
- **Prometheus metrics:** task counts, durations, token refreshes, webhook delivery at `/q/metrics`
- **OpenAPI/Swagger:** A2A endpoints documented at `/q/swagger-ui`
- **Startup validation:** backend + KeyCloak reachability checked at boot
- **Graceful degradation:** user-friendly error messages for backend outages

---

## Part 5: Walkthrough — Testing MCP with Claude

This walkthrough tests the MCP server end-to-end using Claude Desktop.

### Prerequisites
- Java 21, Gradle (or use the wrapper)
- Claude Desktop installed

### Step 1: Start the MCP server
```bash
./gradlew :mcp-server:quarkusDev
```
Wait for `Listening on: http://localhost:8081`. You should see the startup banner.

### Step 2: Connect Claude Desktop
Edit your Claude Desktop config:

**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`
**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`

```json
{
  "mcpServers": {
    "osir": {
      "url": "http://localhost:8081/mcp/sse"
    }
  }
}
```

Restart Claude Desktop. Look for "OSIR" in the tools menu (the hammer icon).

### Step 3: Authenticate
Type in Claude:
> "Log me in to OSIR using the device flow"

Claude will call `loginWithDevice()` and show you a URL + code. Open the URL in your browser, enter the code, and authenticate with KeyCloak.

Then tell Claude:
> "Check if I'm logged in"

Claude calls `getAuthStatus()` and confirms your username.

### Step 4: Test domain operations
Try these prompts:

> "Is example.al available?"

Claude calls `checkDomainAvailability("example.al")` and shows availability + price.

> "Suggest domain names for a pizza restaurant in Tirana"

Claude calls `generateDomainSuggestions("pizza tirana", "al,com")` and presents options.

> "List all my domains"

Claude calls `listUserDomains()` and shows your portfolio.

> "What's the DNS setup for example.al?"

Claude calls `listDnsRecords("example.al")` and displays all records.

### Step 5: Test billing
> "What's my account balance?"

Claude calls `getAccountBalance()` and shows your balance.

> "Show me the pricing for .com domains"

Claude calls `getDomainPricing("com")` and shows registration/renewal prices.

### Step 6: Test prompts
> "How do I set up DNS for a website?"

Claude can use the `dns_setup_guide` prompt for a comprehensive guide with record types, website setup examples, and email configuration.

### What to verify
- Tools appear in Claude's tool menu
- Authentication works (device flow or username/password)
- Domain operations return real data from the backend
- Error messages are clear when something fails

---

## Part 6: Walkthrough — Testing A2A with curl

This walkthrough tests the A2A server end-to-end using curl.

### Prerequisites
- Java 21, Gradle (or use the wrapper)
- curl installed
- A valid KeyCloak bearer token (get one via the MCP device flow or KeyCloak directly)

### Step 1: Start the A2A server
```bash
./gradlew :a2a-server:quarkusDev
```
Wait for `Listening on: http://localhost:8082`.

### Step 2: Verify agent discovery
```bash
# Get the platform agent card
curl -s http://localhost:8082/.well-known/agent.json | jq .

# List all specialist agents
curl -s http://localhost:8082/.well-known/agents | jq '.[].name'
```

Expected output:
```
"OSIR Domain Agent"
"OSIR DNS Agent"
"OSIR VPS & Infrastructure Agent"
"OSIR Billing Agent"
"OSIR Contact Agent"
"OSIR Account & Audit Agent"
"OSIR Orchestrator"
```

### Step 3: Check health and metrics
```bash
# Health check
curl -s http://localhost:8082/q/health/ready | jq .

# Prometheus metrics
curl -s http://localhost:8082/q/metrics | grep a2a

# OpenAPI spec
curl -s http://localhost:8082/q/openapi
```

### Step 4: Send a task (explicit skill routing)
```bash
# Check domain availability — deterministic routing via skill+agent
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-1",
    "method": "tasks/send",
    "params": {
      "id": "walkthrough-1",
      "skill": "check_availability",
      "agent": "domain-agent",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "example.com"}]
      }
    }
  }' | jq .
```

Expected: `result.status` is `"completed"` with an availability artifact.

### Step 5: Send a task (intent-based routing)
```bash
# The system figures out which agent to use from the text
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-2",
    "method": "tasks/send",
    "params": {
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "What is my account balance?"}]
      }
    }
  }' | jq '.result.status'
```

Expected: `"completed"` — routed to the Billing Agent automatically.

### Step 6: Test multi-turn conversation
```bash
# Step 1: Request a transfer without providing the auth code
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-3a",
    "method": "tasks/send",
    "params": {
      "id": "transfer-test",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Transfer example.com to OSIR"}]
      }
    }
  }' | jq '.result.status'
```

Expected: `"input-required"` — agent asks for the EPP code.

```bash
# Step 2: Continue the same task with the auth code
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-3b",
    "method": "tasks/send",
    "params": {
      "id": "transfer-test",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "The auth code is Xk9-mP2q"}]
      }
    }
  }' | jq '.result.status'
```

Expected: `"completed"` or `"failed"` (depending on whether the domain/code is real).

### Step 7: Test the orchestrator
```bash
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-4",
    "method": "tasks/send",
    "params": {
      "skill": "orchestrate",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "Set up example.com with DNS records and check my balance"}]
      }
    }
  }' | jq '.result.artifacts[0]'
```

Expected: workflow-results artifact with steps for domain check, DNS list, and balance check.

### Step 8: Test SSE streaming
```bash
curl -s -X POST http://localhost:8082/a2a/stream \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-5",
    "method": "tasks/sendSubscribe",
    "params": {
      "skill": "list_domains",
      "agent": "domain-agent",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "list my domains"}]
      }
    }
  }'
```

Expected: multiple SSE events showing `submitted` then `working` then `completed`.

### Step 9: Test token refresh
```bash
# Send with an expired token + refresh token
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer EXPIRED_TOKEN" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-6",
    "method": "tasks/send",
    "params": {
      "refreshToken": "YOUR_KEYCLOAK_REFRESH_TOKEN",
      "skill": "get_balance",
      "agent": "billing-agent",
      "message": {
        "role": "user",
        "parts": [{"type": "text", "text": "balance"}]
      }
    }
  }' | jq '.result.metadata.token'
```

Expected: new `accessToken` and `refreshToken` in the response metadata.

### Step 10: Retrieve and cancel a task
```bash
# Get a previous task
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-7",
    "method": "tasks/get",
    "params": { "id": "walkthrough-1" }
  }' | jq '.result.status'

# Cancel a task (only works on non-terminal tasks)
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "test-8",
    "method": "tasks/cancel",
    "params": { "id": "walkthrough-1" }
  }' | jq .
```

### What to verify
- Agent discovery returns all 7 agents
- Health check is green, metrics endpoint has `a2a.tasks.*` counters
- Swagger UI is available at `http://localhost:8082/q/swagger-ui`
- Explicit skill routing works (Step 4)
- Intent-based routing picks the right agent (Step 5)
- Multi-turn conversations maintain state via task ID (Step 6)
- Orchestrator decomposes and executes multi-step workflows (Step 7)
- SSE streaming shows progressive state updates (Step 8)
- Token refresh via KeyCloak returns new tokens (Step 9)
- Task retrieval and cancellation work (Step 10)
- Error messages are user-friendly when backend is down
