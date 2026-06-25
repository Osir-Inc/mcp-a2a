# OSIR Agent Platform

AI integration servers for the [OSIR](https://osir.com) domain registrar. Connect Claude, ChatGPT,
or any AI assistant to real domain, DNS, VPS, billing, and account operations — either as
individual tools (MCP) or as task-solving specialist agents (A2A).

Two servers, one shared backend client library:

| Server | Port | Protocol | What it gives an AI |
|--------|------|----------|---------------------|
| **MCP Server** | 8081 | [Model Context Protocol](https://modelcontextprotocol.io) (SSE + Streamable HTTP) | 71 fine-grained tools (`checkDomainAvailability`, `registerDomain`, `createDnsRecord`, …) + 10 guided prompts |
| **A2A Server** | 8082 | [Google Agent-to-Agent](https://google.github.io/A2A/) (JSON-RPC 2.0) | 7 specialist agents with 50+ skills and an orchestrator for multi-step workflows |

Use **MCP** when one assistant should call individual operations. Use **A2A** when you want to hand
a whole task ("set up example.com with DNS and check my balance") to agents that coordinate the work.

---

## What is MCP?

MCP (Model Context Protocol) is an open standard that connects AI assistants to external tools and
data. An MCP server publishes a set of callable tools; any MCP client — Claude Desktop, Cursor,
Copilot, or a custom agent — can discover and invoke them. OSIR implements an MCP server
purpose-built for domain and infrastructure management, which is what makes it an AI-native
registrar rather than a traditional one with a chat box bolted on.

## Connect OSIR to Claude

Add OSIR as a custom connector from your Claude settings — no config file or install required:

1. In Claude, open **Customize → Connectors**.
2. Click **+**, then choose **Add custom connector**.
3. Set **Name** to `OSIR`.
4. Set the **Remote MCP server URL** to `https://be.osir.com/mcp/http`.
5. Save, then authenticate with your OSIR account when prompted.

```
Claude · Add custom connector

Name:                OSIR
Remote MCP server:   https://be.osir.com/mcp/http
```

The same remote MCP server URL `https://be.osir.com/mcp/http` works in any MCP client that supports
a remote (streamable HTTP) server.

### What your assistant can do

| Capability | Example request |
|------------|-----------------|
| Search availability | "Is coolstartup.io available, and what does it cost?" |
| Register a domain | "Register it for two years with WHOIS privacy." |
| Manage DNS | "Point it at 192.0.2.10 and add my email records." |
| Renew and transfer | "Renew everything expiring in the next 30 days." |
| Provision a VPS | "Spin up a 2 vCPU server in Frankfurt running Ubuntu." |

---

## Running the servers yourself

Want to host your own instance or develop against the code? Requires **Java 21**; Gradle is bundled
via the wrapper.

```bash
# Run the MCP server (port 8081)
./gradlew :mcp-server:quarkusDev

# Run the A2A server (port 8082)
./gradlew :a2a-server:quarkusDev
```

Copy `.env.example` to `.env` and adjust if you're pointing at your own backend/KeyCloak/Ollama.
Everything defaults to the public OSIR endpoints, so the servers run out of the box.

### Connect an MCP client (Claude Desktop / Claude Code)

Add to your client's MCP config (e.g. `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "osir": { "url": "http://localhost:8081/mcp/sse" }
  }
}
```

Restart the client and the OSIR tools appear. Then just ask:

> "Is pizzashqip.al available? If not, suggest alternatives."
> "List all my domains and show which expire in the next 30 days."
> "Add an A record pointing example.al to 203.0.113.10 and a CNAME for www."

Most operations need authentication — ask the assistant to *"log me in to OSIR using the device
flow"* and it walks you through browser-based OAuth (KeyCloak, RFC 8628).

### Call the A2A server

```bash
# Discover the agents
curl http://localhost:8082/.well-known/agents | jq '.[].name'

# Send a task (the platform routes it to the right specialist)
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0", "id": "1", "method": "tasks/send",
    "params": { "message": { "role": "user",
      "parts": [{"type": "text", "text": "Check if example.com is available"}] } }
  }'
```

Tasks stream over `POST /a2a/stream` (SSE), support multi-turn `input-required` flows, and the
orchestrator decomposes complex requests across agents (max 15 steps).

---

## What you can do

**Domains** — availability, registration, renewal, transfer, lock/unlock, auto-renew, WHOIS
privacy, nameservers, AI-powered name suggestions.
**DNS** — list/create/update/delete records.
**VPS** — browse packages and locations, order, manage, panel login.
**Billing** — balance, invoices, payments, fee previews, domain pricing.
**Contacts**, **Transfers**, **Hosts**, **Audit logs**, **Account** profile & summary.

Full tool/skill catalog, example conversations, and end-to-end walkthroughs are in **[GUIDE.md](docs/GUIDE.md)**.

---

## Build, test, deploy

```bash
./gradlew build        # build all modules + run tests
./gradlew test         # tests only

docker-compose up -d   # run both servers in containers
```

The provided `docker-compose.yml`, `build-and-deploy.bat`, and the CI workflow reference a
placeholder container registry (`registry.example.com`) — point them at your own.
See **[DEPLOYMENT.md](docs/DEPLOYMENT.md)** for the production checklist (PostgreSQL, TLS, scaling).

## Configuration

All settings are environment variables with sensible defaults — nothing secret is committed.

| Variable | Default | Description |
|----------|---------|-------------|
| `OSIR_BACKEND_URL` | `https://be.osir.com` | Backend API |
| `KEYCLOAK_URL` | `https://auth.osir.com` | KeyCloak auth server |
| `KEYCLOAK_REALM` | `osir` | KeyCloak realm |
| `KEYCLOAK_CLIENT_ID` | `osir-cli` | OAuth client ID |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama LLM (MCP chat UI) |
| `CORS_ORIGINS` | `https://osir.com,…` | Allowed CORS origins |
| `A2A_SIGNING_SECRET` | *(empty)* | Optional HMAC-SHA256 request signing |

See [`.env.example`](.env.example) for the full list.

## Project layout

```
common/      Shared library — 12 services, 9 REST clients, ~174 models
mcp-server/  Quarkus MCP server — 71 tools, 10 prompts, chat UI
a2a-server/  Quarkus A2A server — 7 agents, JSON-RPC, JPA task persistence
```

Both servers depend on `common`, so a backend operation is implemented once and exposed two ways.

## Documentation

- **[GUIDE.md](docs/GUIDE.md)** — complete usage guide, tool reference, walkthroughs
- **[A2A-API-REFERENCE.md](docs/A2A-API-REFERENCE.md)** — A2A protocol spec (methods, errors, agents)
- **[A2A-ARCHITECTURE.md](docs/A2A-ARCHITECTURE.md)** — A2A design document
- **[DEPLOYMENT.md](docs/DEPLOYMENT.md)** — production deployment checklist

## License

[Apache License 2.0](LICENSE).
