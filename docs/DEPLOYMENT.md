# OSIR MCP & A2A — Production Deployment Checklist

## Prerequisites

- Docker installed on the production server
- Access to `registry.example.com`
- KeyCloak realm `osir` configured at `auth.osir.com` with clients `osir-cli` and `mcp-client` (below)
- Backend API running at `be.osir.com`

## Keycloak configuration (realm `osir`)

The MCP server offers two connector URLs, and each uses its own Keycloak client:

| Connector URL | Sign-in | Keycloak client | Used by |
|---|---|---|---|
| `https://be.osir.com/mcp/http` | In-chat device login (RFC 8628); the tool returns a `sessionKey` | `osir-cli` | Claude "No sign-in", ChatGPT "No authentication", Grok, the CLI |
| `https://be.osir.com/mcp/oauth` | OAuth authorization code + PKCE, run by the chat app | `mcp-client` | Claude "Sign in now", ChatGPT "OAuth" |

The user-facing setup guides are `docs/CONNECT-CLAUDE.md` and `docs/CONNECT-CHATGPT-GROK.md`.

### Client `osir-cli` (device login)

| Setting | Value |
|---|---|
| Client authentication | Off (public client) |
| OAuth 2.0 Device Authorization Grant | On |
| PKCE method | S256 (realm policy; the MCP server sends PKCE on the device grant) |

`osir-cli` is shared with the CLI. Don't change its session settings for the MCP's sake: the MCP enforces its own 30 min idle / 8 h max on `sessionKey` sessions (`MCP_SESSION_IDLE_MINUTES`, `MCP_SESSION_MAX_HOURS`).

### Client `mcp-client` (OAuth connectors) — hardened 2026-09-18

| Setting | Value | Why |
|---|---|---|
| Client authentication | Off (public client) | Chat apps can't keep a secret; users enter only the client id |
| Standard flow | On | The OAuth browser sign-in |
| Direct access grants | **Off** | On, anyone could trade a username + password for a token with no login page (no MFA, no brute-force screen) |
| Device authorization grant | Off | Device login uses `osir-cli` |
| PKCE method (Advanced) | S256 | Stolen authorization codes are useless without the verifier |
| Valid redirect URIs | `https://claude.ai/*`, `https://claude.com/*`, `https://chatgpt.com/*` | Claude calls back to `/api/mcp/auth_callback` on both hosts; ChatGPT to `/connector_platform_oauth_redirect`. Exact URLs are safer than wildcards but break if a vendor changes its path |
| Web origins | empty | The token exchange runs on the vendor's servers, not in a browser |
| Client Session Idle / Max (Advanced) | **30 min / 8 h** (1800 s / 28800 s) | Matches the No sign-in option; after that the user signs in again. Must stay below the realm SSO limits (12 h idle / 7 days max), or Keycloak rejects the value |
| Default client scopes | must include **`roles`** (plus `basic`, `profile`, `email`, `web-origins`, `acr`) | Without `roles` the token has no `realm_access.roles` and every tool call gets 403 from the backend. This is why dynamically registered clients don't work |
| Optional client scopes | **no `offline_access`** | An offline token ignores the client session limits and, with the realm's 30-day offline idle and no max, never expires while used |
| Full scope allowed | On | Needed for the backend role check |

Grok needs no Keycloak entry: its custom connectors can't do OAuth, so Grok users use `/mcp/http`.

When a new chat platform adds OAuth support, add its callback to Valid redirect URIs. `scripts/check-oauth-redirects.sh` (run on the Keycloak host) lists logins Keycloak rejected, grouped by client and redirect URI, so a missing callback shows up there first.

### Anonymous client registration (DCR): must stay CLOSED

Keycloak lets anyone create a client in the realm without logging in (Dynamic Client Registration, `POST /realms/osir/clients-registrations/openid-connect`) unless the anonymous **Trusted Hosts** registration policy refuses it. Nothing of ours uses anonymous DCR: Claude and ChatGPT sign in through `mcp-client`, and our OAuth metadata doesn't advertise a `registration_endpoint`. An open endpoint only lets strangers create clients (a client secret is returned) and pile them up to the Max Clients Limit (200).

**Required state:** an anonymous registration policy with `providerId: trusted-hosts` and config
```json
{"trusted-hosts": [], "host-sending-registration-request-must-match": ["true"], "client-uris-must-match": ["true"]}
```
An empty host list makes Keycloak answer every anonymous registration with **403 "Host not trusted"**. Admins can still create clients in the console.

**History:**
- 2026-07-18: anonymous DCR opened to `claude.ai`/`claude.com` for Claude.ai URL-only OAuth. Abandoned: DCR clients get no `roles` scope, so their tokens got 403.
- 2026-07-30: Trusted Hosts emptied again (closed).
- 2026-09-15: found open again (an external audit). Cause, found 2026-09-18: the anonymous Trusted Hosts policy had been **deleted**, not just edited. Keycloak then accepts anonymous registrations.
- 2026-09-18 14:44 UTC: policy recreated with an empty host list via `scripts/keycloak-close-anonymous-dcr.sh`; `scripts/smoke-prod.sh` confirms 403. Backup of the policies before the change: `kc-registration-policies-20260918-144440.json` (kept outside the repo in the admin's home folder, `C:\Users\mandi\kc-registration-policies-20260918-144440.json`; the script now writes backups to `$HOME`, and `.gitignore` excludes `kc-registration-policies-*.json`).

**Check it:** `scripts/smoke-prod.sh`, check "anonymous client registration refused". If registration succeeds, the script deletes its own test client right away (RFC 7592, using the client's registration token) and reports FAIL.

**Fix it if it reopens:** from a host whitelisted for the admin API (the admin's WSL; the servers are not whitelisted), run `bash scripts/keycloak-close-anonymous-dcr.sh`. It asks for the master admin login (the password is never echoed or stored), lists the anonymous policies and any clients with generated (UUID) ids, saves a backup, and only changes anything after you type `y`.

**Revert** (reopens anonymous DCR exactly as before). Delete the recreated policy with a master admin token:
```bash
KC=https://auth.osir.com
TYPE=org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy
ID=$(curl -s -H "Authorization: Bearer $TOKEN" "$KC/admin/realms/osir/components?type=$TYPE" \
  | jq -r '.[] | select(.subType=="anonymous" and .providerId=="trusted-hosts") | .id')
curl -X DELETE -H "Authorization: Bearer $TOKEN" "$KC/admin/realms/osir/components/$ID"
```

**Clean up leftover clients:** registrations made while DCR was open remain as clients with UUID client ids. Review each one in **Clients** and delete it (**Action → Delete**). Never delete `mcp-client`, `osir-cli` or the backend's own clients. Pending on 2026-09-18: `1e98735c-…` (name "verify", redirect `https://x.invalid/cb`, a test probe) and `c974c4a8-…` (no name, no redirect URIs; check before deleting).

This realm's admin console (Keycloak 26.7) doesn't show client registration policies, so use the Admin REST API (as the script does) to view or change them.

## Step 1: Build and Push Images

On your build machine (Windows):

```batch
:: Build all modules (runs 351 tests)
gradlew.bat build

:: Build and push Docker images
build-and-deploy.bat
```

This pushes:
- `registry.example.com/com-osir-mcp:latest`
- `registry.example.com/com-osir-a2a:latest`

Verify the images were pushed:
```bash
docker pull registry.example.com/com-osir-mcp:latest
docker pull registry.example.com/com-osir-a2a:latest
```

## Step 2: Prepare the Production Server

SSH into your production server and create the deployment directory:

```bash
mkdir -p /opt/osir-agent
cd /opt/osir-agent
```

## Step 3: Create the `.env` File

```bash
cat > .env << 'EOF'
# Backend
OSIR_BACKEND_URL=https://be.osir.com

# KeyCloak
KEYCLOAK_URL=https://auth.osir.com
KEYCLOAK_REALM=osir
KEYCLOAK_CLIENT_ID=osir-cli

# Ollama LLM
OLLAMA_URL=http://localhost:11434

# CORS — add your frontend domains
CORS_ORIGINS=https://osir.com,https://agent.osir.com

# MCP audit trail — one line per tool call and per confirmed action, append-only.
# Must sit on the mounted ./data volume or it is lost with the container.
AUDIT_LOG_PATH=/app/data/audit.log

# A2A agent card — public HTTPS base URL (behind a TLS-terminating proxy).
# Sets the card's absolute `url`. Omit to derive from the request.
A2A_PUBLIC_URL=https://be.osir.com

# Agent telemetry funnel (POST /v1/agent/telemetry). API key with role api-user,
# issued for the MCP service; sent as X-API-Key. Leave empty to disable telemetry.
OSIR_TELEMETRY_API_KEY=

# Rate limiting
A2A_RATE_LIMIT_GLOBAL=50
A2A_RATE_LIMIT_PER_USER=10

# Request signing (leave empty to disable)
A2A_SIGNING_SECRET=
A2A_SIGNING_REQUIRED=false
EOF
```

**Important:** If you want PostgreSQL instead of H2 for task persistence, add:
```bash
cat >> .env << 'EOF'

# PostgreSQL (replaces H2 file-based default)
QUARKUS_DATASOURCE_DB_KIND=postgresql
QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost:5432/osir_a2a
QUARKUS_DATASOURCE_USERNAME=osir
QUARKUS_DATASOURCE_PASSWORD=your-secure-password
EOF
```

## Step 4: Create `docker-compose.yml`

```bash
cat > docker-compose.yml << 'EOF'
version: '3.8'

services:
  com-osir-mcp:
    image: registry.example.com/com-osir-mcp:latest
    container_name: com-osir-mcp
    ports:
      - "8081:8081"
    volumes:
      - ./data:/app/data
    env_file:
      - .env
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/q/health/ready"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped

  com-osir-a2a:
    image: registry.example.com/com-osir-a2a:latest
    container_name: com-osir-a2a
    ports:
      - "8082:8082"
    volumes:
      - ./data:/app/data
    env_file:
      - .env
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8082/q/health/ready"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    restart: unless-stopped
EOF
```

## Step 5: Deploy

```bash
# Pull latest images
docker-compose pull

# Start both services
docker-compose up -d

# Check containers are running
docker-compose ps

# Watch startup logs
docker-compose logs -f --tail=50
```

## Step 6: Verify

### Health checks
```bash
# MCP server
curl -s http://localhost:8081/q/health/ready | jq .status
# Expected: "UP"

# A2A server
curl -s http://localhost:8082/q/health/ready | jq .status
# Expected: "UP"
```

### MCP connectivity + auth surface: run the smoke test after EVERY deploy
```bash
./scripts/smoke-prod.sh            # production (BASE=https://be.osir.com, KC=https://auth.osir.com/realms/osir)
# Expected: "All checks passed." and exit code 0 (exit code = number of failed checks)
```
It is read-only against the MCP server. Against Keycloak it only sends requests that must be refused; they appear in Keycloak's event log with host `smoke-test.invalid` and user `smoke-test-nonexistent`, which is expected. What it checks, each tied to a past incident:

| Area | Check |
|---|---|
| `/mcp/http` | `initialize` without a token → 200; `tools/list` ≥ 90 tools; anonymous `checkDomainAvailability` works; `/.well-known/oauth-protected-resource/mcp/http` → **404, never 401** (a 401 there makes Claude.ai refuse "No sign-in", incident 2026-09-18) |
| `/mcp/oauth` | no token → 401 with `resource_metadata` pointing at `/.well-known/oauth-protected-resource/mcp/oauth`; that document → 200 with `resource` = `/mcp/oauth` |
| Metadata | root protected-resource document names `/mcp/http`; AS metadata issuer = Keycloak realm, and **no** `registration_endpoint` |
| Keycloak `mcp-client` | Claude.ai, Claude.com and ChatGPT callbacks accepted; a foreign redirect refused (400); password grant refused as `unauthorized_client` |
| Keycloak realm | anonymous client registration refused (403) |
| Other | `/mcp/health` and `/.well-known/agent.json` → 200 |

Production runs with `MCP_OAUTH_CHALLENGE_ENABLED=false`: `/mcp/http` must accept requests without a token, and `/mcp/oauth` is challenged regardless of that flag.

### A2A agent discovery
```bash
curl -s http://localhost:8082/.well-known/agent.json | jq .name
# Expected: "OSIR Agent Platform"

curl -s http://localhost:8082/.well-known/agents | jq '.[].name'
# Expected: 7 agent names
```

### A2A task execution (without auth — will show auth error, but proves the endpoint works)
```bash
curl -s -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "health-test",
    "method": "tasks/send",
    "params": {
      "skill": "get_balance",
      "agent": "billing-agent",
      "message": {"role": "user", "parts": [{"type": "text", "text": "balance"}]}
    }
  }' | jq '.result.status'
# Expected: "completed" or "failed" (auth required)
```

### Prometheus metrics
```bash
curl -s http://localhost:8082/q/metrics | grep a2a.tasks
# Expected: a2a.tasks.created_total, a2a.tasks.completed_total, etc.
```

### Swagger UI
Open in browser: `http://your-server:8082/q/swagger-ui`

## Step 7: Configure Reverse Proxy

Full nginx config for `be.osir.com` (`/etc/nginx/sites-available/be.osir.com` or equivalent):

```nginx
# OAuth protected-resource metadata (step 1 in Claude connector discovery).
# Prefix match (^~), not exact: it must also cover the path-suffixed document for the
# always-OAuth URL, /.well-known/oauth-protected-resource/mcp/oauth. Without it, the request
# falls through to the backend, which answers 401 and breaks OAuth discovery.
location ^~ /.well-known/oauth-protected-resource {
    proxy_pass         http://127.0.0.1:8081;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Forwarded-Proto $scheme;
    add_header         Cache-Control     "public, max-age=3600";
}

# OAuth AS metadata (step 2 — connector follows authorization_servers from above)
location = /.well-known/oauth-authorization-server {
    proxy_pass         http://127.0.0.1:8081/.well-known/oauth-authorization-server;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Forwarded-Proto $scheme;
    add_header         Cache-Control     "public, max-age=3600";
}

# A2A agent card
location = /.well-known/agent.json {
    proxy_pass http://127.0.0.1:8082/.well-known/agent.json;
    proxy_set_header Host $host;
}
# NOTE: the proxy terminates TLS and forwards plain HTTP, so the app would derive
# an http:// card `url`. Pin the public HTTPS base so the card advertises https:
#   A2A_PUBLIC_URL=https://be.osir.com   (env var; sets the card's `url` field)

# MCP Server — SSE + Streamable HTTP (all /mcp/* paths)
location /mcp {
    proxy_pass         http://127.0.0.1:8081;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   Connection        "";
    proxy_buffering    off;
    proxy_cache        off;
    proxy_read_timeout 3600s;
    chunked_transfer_encoding on;
}

# A2A Server — JSON-RPC
location /a2a {
    proxy_pass         http://127.0.0.1:8082/a2a;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   X-Request-ID      $request_id;
    client_max_body_size 256k;
    proxy_read_timeout 60s;
}

# A2A SSE streaming
location /a2a/stream {
    proxy_pass         http://127.0.0.1:8082/a2a/stream;
    proxy_http_version 1.1;
    proxy_set_header   Host              $host;
    proxy_set_header   X-Real-IP         $remote_addr;
    proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_set_header   X-Forwarded-Proto $scheme;
    proxy_set_header   X-Request-ID      $request_id;
    proxy_set_header   Connection        "";
    proxy_buffering    off;
    proxy_cache        off;
    proxy_read_timeout 3600s;
    chunked_transfer_encoding on;
}
```

After editing:
```bash
nginx -t && nginx -s reload
```

## Step 8: Monitor

### Logs
```bash
# All logs
docker-compose logs -f

# MCP only
docker-compose logs -f com-osir-mcp

# A2A only
docker-compose logs -f com-osir-a2a

# Search for errors
docker-compose logs --since 1h | grep -i error

# Audit trail (MCP): every tool call, and every confirmed destructive/financial action
tail -f data/audit.log
grep 'confirmed action_id' data/audit.log
```

### Container health
```bash
# Health status
docker inspect --format='{{.State.Health.Status}}' com-osir-mcp
docker inspect --format='{{.State.Health.Status}}' com-osir-a2a
```

### Resource usage
```bash
docker stats com-osir-mcp com-osir-a2a --no-stream
```

## Updating

```bash
cd /opt/osir-agent

# Pull new images
docker-compose pull

# Restart with zero-downtime (one at a time)
docker-compose up -d --no-deps com-osir-mcp
docker-compose up -d --no-deps com-osir-a2a

# Verify health
curl -s http://localhost:8081/q/health/ready | jq .status
curl -s http://localhost:8082/q/health/ready | jq .status
```

## Rollback

```bash
# If something goes wrong, roll back to previous image
docker-compose down
docker tag registry.example.com/com-osir-mcp:previous registry.example.com/com-osir-mcp:latest
docker tag registry.example.com/com-osir-a2a:previous registry.example.com/com-osir-a2a:latest
docker-compose up -d
```

## Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| Container exits immediately | Missing .env file | Create `.env` with required vars |
| Health check failing | Backend or KeyCloak unreachable | Check `.env` URLs, verify network connectivity |
| 401 on all A2A requests | Wrong KEYCLOAK_CLIENT_ID | Verify `osir-cli` client exists in KeyCloak realm |
| H2 database locked | Two containers sharing same data volume | Use PostgreSQL or separate data dirs |
| MCP SSE connection drops | Proxy buffering enabled | Set `proxy_buffering off` in nginx |
| Rate limit 429 errors | Too many concurrent requests | Increase `A2A_RATE_LIMIT_*` in `.env` |
| Startup log shows UNREACHABLE | Backend/KeyCloak DNS not resolving | Check DNS, add to `/etc/hosts` if needed |
| Claude/ChatGPT OAuth popup fails | `Standard flow` not enabled on `mcp-client`, or the user didn't enter client id `mcp-client` | Enable in KeyCloak → Clients → mcp-client → Settings; see `docs/CONNECT-CLAUDE.md` |
| `smoke-prod.sh`: "anonymous client registration refused" FAILs (HTTP 201) | Anonymous Trusted Hosts registration policy missing or has hosts again | `bash scripts/keycloak-close-anonymous-dcr.sh` from WSL; see "Anonymous client registration" above |
| Keycloak rejects Client Session Idle/Max on `mcp-client` | Value is longer than the realm SSO limits, often because the unit dropdown was left on Hours | Enter 30 **Minutes** / 8 **Hours** under Clients → mcp-client → Advanced |
| OAuth redirect_uri mismatch | Claude's redirect URI not whitelisted | Add `https://claude.ai/*` and `https://claude.com/*` to Valid Redirect URIs on client `mcp-client` |
| `/.well-known/oauth-protected-resource` returns 404 | nginx location missing or not reloaded | Add the location block; run `nginx -s reload` |
| `/.well-known/oauth-protected-resource/mcp/oauth` returns 401 (empty body, `www-authenticate: Bearer`) | nginx uses the exact-match `=` location, so the backend answers | Switch to the `^~` prefix location above; run `nginx -s reload` |
| Claude.ai: "set up as not requiring sign-in, but the server asked for sign-in when checked (status 401)" | Claude's check fetches `/.well-known/oauth-protected-resource/mcp/http`; with the `=` location, the backend answers 401 (the MCP server's answer is 404, the correct result) | `^~` location as above; the user must then **remove** the connector and add it again, because Claude caches the failed check |
| `/.well-known/oauth-authorization-server` returns 404 | nginx location missing or not reloaded | Add the exact-match location block; run `nginx -s reload` |
| Claude's "Register automatically" / "published identity" fails | Not supported: DCR clients get no roles, so their tokens are rejected with 403. The metadata doesn't advertise DCR | Users choose "Use your own OAuth client" with `mcp-client` |
| MCP tools return "not authenticated" with valid token | Token failed local validation (expired/wrong issuer) | Check token `iss` matches `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}` |
| `/mcp/oauth` returns 200 without token | Old image without the `/mcp/oauth` gate | Rebuild and redeploy the MCP image (`/mcp/http` returning 200 without a token is correct in production) |
