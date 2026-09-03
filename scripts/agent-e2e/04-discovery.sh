#!/usr/bin/env bash
# 04-discovery.sh - asserts every discovery/metadata URL an agent needs is live,
# unauthenticated, and carries the fields the specs require (audit F3/F5/F7 acceptance).
#
# Usage: ./04-discovery.sh [BACKEND_BASE] [APEX_BASE]
#   BACKEND_BASE default https://be.osir.com
#   APEX_BASE    default https://osir.com   (apex checks WARN instead of FAIL - marketing-owned, C.2)
set -u

BASE="${1:-https://be.osir.com}"
APEX="${2:-https://osir.com}"
PASS=0; FAIL=0; WARN=0

ok()   { PASS=$((PASS+1)); echo "PASS  $1"; }
bad()  { FAIL=$((FAIL+1)); echo "FAIL  $1"; }
meh()  { WARN=$((WARN+1)); echo "WARN  $1"; }

# check <name> <url> [required-substring...]
check() {
  local name="$1" url="$2"; shift 2
  local body code
  body=$(curl -sS -m 15 "$url" -w $'\n%{http_code}' 2>/dev/null) || { bad "$name: curl failed ($url)"; return; }
  code="${body##*$'\n'}"; body="${body%$'\n'*}"
  [ "$code" = "200" ] || { bad "$name: HTTP $code ($url)"; return; }
  local s
  for s in "$@"; do
    case "$body" in *"$s"*) ;; *) bad "$name: 200 but missing '$s' ($url)"; return;; esac
  done
  ok "$name"
}

# Same as check but only warns (apex is marketing-owned).
check_apex() {
  local name="$1" url="$2"; shift 2
  local body code
  body=$(curl -sS -m 15 "$url" -w $'\n%{http_code}' 2>/dev/null) || { meh "$name: curl failed ($url)"; return; }
  code="${body##*$'\n'}"; body="${body%$'\n'*}"
  if [ "$code" != "200" ]; then meh "$name: HTTP $code ($url)"; return; fi
  local s
  for s in "$@"; do
    case "$body" in *"$s"*) ;; *) meh "$name: 200 but missing '$s' ($url)"; return;; esac
  done
  ok "$name (apex)"
}

echo "== Discovery checks against $BASE (apex: $APEX) =="

# --- OAuth metadata (F3/F4) ---
check "oauth-protected-resource" "$BASE/.well-known/oauth-protected-resource" \
  '"authorization_servers"' '"resource"'
check "oauth-authorization-server" "$BASE/.well-known/oauth-authorization-server" \
  '"authorization_endpoint"' '"token_endpoint"'

# --- A2A agent card (F7) ---
check "agent card" "$BASE/.well-known/agent.json" \
  '"skills"' '"capabilities"' '"securitySchemes"' '"tags"'

# --- Public REST surface (F5, backend-owned but asserted here) ---
check "openapi.json public" "$BASE/openapi.json" '"openapi"'
check "public availability (F1)" "$BASE/v1/public/catalog/domains/discovery-check-2026.com/availability" \
  '"available"'
check "public hosting bundle" "$BASE/v1/public/catalog/bundle?domain=discovery-check-2026.com" \
  '"options"' '"nextSteps"'

# --- MCP initialize: instructions + serverInfo (A.4) ---
INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"04-discovery","version":"1"}}}'
body=$(curl -sS -m 20 -X POST "$BASE/mcp/http" \
  -H "Content-Type: application/json" -H "Accept: application/json, text/event-stream" \
  -d "$INIT" 2>/dev/null)
case "$body" in
  *'"instructions"'*'"serverInfo"'*|*'"serverInfo"'*'"instructions"'*) ok "mcp initialize (instructions + serverInfo)";;
  *'"WWW-Authenticate"'*|"") bad "mcp initialize: no body";;
  *) bad "mcp initialize: missing instructions/serverInfo (got: $(printf '%s' "$body" | head -c 120)...)";;
esac

# 401 challenge shape when unauthenticated OAuth mode is enabled: informational only,
# because production may legitimately run with the challenge disabled for URL-only connectors.
hdr=$(curl -sS -m 15 -o /dev/null -D - -X POST "$BASE/mcp/http" -H "Content-Type: application/json" -d '{}' 2>/dev/null | tr -d '\r')
case "$hdr" in
  *"WWW-Authenticate:"*resource_metadata*) ok "401 challenge advertises resource_metadata";;
  *"HTTP/"*" 200"*|*"HTTP/2 200"*) meh "mcp endpoint answers anonymously (challenge disabled mode)";;
  *) meh "mcp endpoint: unexpected unauthenticated response";;
esac

# --- Apex discovery (C.2, marketing-owned: warn only) ---
check_apex "apex agent.json" "$APEX/.well-known/agent.json" '"skills"'
check_apex "apex llms.txt" "$APEX/llms.txt" "osir"
check_apex "apex robots.txt" "$APEX/robots.txt" "User-agent"

echo
echo "== $PASS passed, $FAIL failed, $WARN warnings =="
[ "$FAIL" -eq 0 ]
