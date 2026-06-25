# OSIR MCP & A2A — Production Deployment Checklist

## Prerequisites

- Docker installed on the production server
- Access to `registry.example.com`
- KeyCloak realm `osir` configured at `auth.osir.com` with client `osir-cli`
- Backend API running at `be.osir.com`

### KeyCloak client requirements for OAuth 2.1 MCP auth

In KeyCloak admin (`https://auth.osir.com`), open **Clients → osir-cli**:

| Setting | Value |
|---------|-------|
| Client authentication | Off (public client) |
| Standard Flow | ✅ Enabled |
| Device Authorization Grant | ✅ Enabled |
| Valid Redirect URIs | `https://claude.ai/*`, `https://*.anthropic.com/*`, your own app URLs |
| Web Origins | `https://claude.ai`, `https://*.anthropic.com` (for CORS on token endpoint) |

> Without `Standard Flow` enabled, the OAuth browser popup from Claude's connector will fail.

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

### MCP connectivity
```bash
# Protected-resource metadata (no auth required)
curl -s http://localhost:8081/.well-known/oauth-protected-resource | jq .
# Expected: { "resource": "https://be.osir.com/mcp/http", "authorization_servers": ["https://be.osir.com"], ... }

# AS metadata (no auth required)
curl -s http://localhost:8081/.well-known/oauth-authorization-server | jq .issuer
# Expected: "https://auth.osir.com/realms/osir"

# Unauthenticated request should return 401 with WWW-Authenticate
curl -si -X POST http://localhost:8081/mcp/http \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  | head -5
# Expected: HTTP/1.1 401 and WWW-Authenticate: Bearer realm="OSIR MCP" ...

# Authenticated request (replace TOKEN with a valid KeyCloak Bearer token)
curl -s -X POST http://localhost:8081/mcp/http \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0"}}}' \
  | jq .result.serverInfo
# Expected: {"name":"Domain Registrar MCP Server","version":"2.0.0"}
```

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
# OAuth protected-resource metadata (step 1 in Claude connector discovery)
location = /.well-known/oauth-protected-resource {
    proxy_pass         http://127.0.0.1:8081/.well-known/oauth-protected-resource;
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
| Claude connector OAuth popup fails | `Standard Flow` not enabled on `osir-cli` | Enable in KeyCloak → Clients → osir-cli → Settings |
| OAuth redirect_uri mismatch | Claude's redirect URI not whitelisted | Add `https://claude.ai/*` to Valid Redirect URIs in KeyCloak |
| `/.well-known/oauth-protected-resource` returns 404 | nginx location missing or not reloaded | Add exact-match location block; run `nginx -s reload` |
| `/.well-known/oauth-authorization-server` returns 404 | nginx location missing or not reloaded | Add the exact-match location block; run `nginx -s reload` |
| DCR (Dynamic Client Registration) returns 401 | Anonymous access not enabled in KeyCloak | KeyCloak → Realm Settings → Client Registration → Anonymous Access Policies → enable `Allowed Client Scopes` for openid-connect |
| MCP tools return "not authenticated" with valid token | Token failed local validation (expired/wrong issuer) | Check token `iss` matches `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}` |
| MCP endpoint still returns 200 without token | `@RouteFilter` not firing (quarkus-vertx-web missing) | Verify `quarkus-vertx-web` is in `mcp-server/build.gradle` and image was rebuilt |
