# Directory submission checklist

Human-submitted; everything below is prepared. Bump `server.json` version AND
`quarkus.mcp.server.server-info.version` on every tool change before republishing anywhere.

## Assets (shared)

| Asset | Value / location |
|---|---|
| MCP endpoint (Streamable HTTP) | `https://be.osir.com/mcp/http` (also `https://be.osir.com/mcp`) |
| Legacy SSE endpoint | `https://be.osir.com/mcp/sse` |
| OAuth protected-resource metadata | `https://be.osir.com/.well-known/oauth-protected-resource` |
| OAuth authorization-server metadata | `https://be.osir.com/.well-known/oauth-authorization-server` |
| A2A agent card | `https://be.osir.com/.well-known/agent.json` |
| Icon (400x400 PNG) | `https://osir.com/og-image.png` |
| Server manifest | `server.json` (repo root) |
| Source repo | `https://github.com/Osir-Inc/mcp-a2a` |
| Support/contact | support@osir.com |
| Verification script | `scripts/agent-e2e/04-discovery.sh` (run before every submission) |

## 1. Official MCP Registry (registry.modelcontextprotocol.io)

Listed as `com.osir/domain-registrar` (last published: 2.5.6 with icons, 2026-11-18).

1. Namespace `com.osir/*` is proven by HTTP auth: `https://osir.com/.well-known/mcp-registry-auth`
   (served by the osir.com site). The matching Ed25519 `key.pem` is kept gitignored in the
   osir.com repo under `scripts/mcp-registry/`, on the maintainer's machine only.
2. Publish with [`mcp-publisher`](https://github.com/modelcontextprotocol/registry/releases)
   from the repo root (it reads `./server.json`):
   ```bash
   PRIVATE_KEY="$(openssl pkey -in key.pem -noout -text | grep -A3 'priv:' | tail -n +2 | tr -d ' :\n')"
   mcp-publisher login http --domain osir.com --private-key "$PRIVATE_KEY"
   mcp-publisher validate      # checks against the live registry
   mcp-publisher publish
   ```
3. Republish requires a HIGHER `version` than the last accepted one, and it must equal the live
   `serverInfo.version` - so publish after the release is deployed.
4. `description` is capped at 100 characters (a longer one fails `validate` with 422).
5. `server.json` is not in the Docker images: publishing needs no rebuild or deploy.

## 2. Claude connector directory (Anthropic)

- Submission form: https://www.anthropic.com/partners/mcp (directory intake; form link may
  change - start from the Claude docs "remote MCP servers" page).
- Required: server name (OSIR), remote URL (`https://be.osir.com/mcp/http`), OAuth support
  (yes - DCR + PKCE, no pre-registered client needed), icon, short + long description,
  support contact, privacy policy URL (`https://osir.com/en/privacy/`).
- Directory review rejects servers without tool annotations - shipped 2026-09-02 (all 105
  tools carry titles + readOnly/destructive/idempotent hints).

## 3. ChatGPT apps / connectors directory (OpenAI)

- Requires an OpenAI Platform account with the org verified; submit via the connectors
  registry in the developer dashboard.
- Same remote URL + OAuth metadata; OpenAI requires parameter descriptions on all tools
  (shipped 2026-09-02) and a published privacy policy.

## Pre-submission gate (every time)

- [ ] `scripts/agent-e2e/04-discovery.sh` passes against production
- [ ] `server.json` version == `server-info.version` == deployed serverInfo version
- [ ] Fresh custom-connector setup tested in Claude.ai AND ChatGPT with no manual
      Keycloak client creation (verifies anonymous DCR end to end)
- [ ] Scorecard re-run; record the new score in docs/agent-readiness/PLAN.md
