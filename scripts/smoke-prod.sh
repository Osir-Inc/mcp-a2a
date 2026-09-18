#!/usr/bin/env bash
# Post-deploy smoke test for the public MCP + OAuth surface. Read-only against the MCP server;
# against Keycloak it only makes requests that must be refused (they show up in Keycloak's event
# log as LOGIN_ERROR / registration errors from host smoke-test.invalid, which is expected).
#
#   ./scripts/smoke-prod.sh                       # production
#   BASE=https://staging.example ./scripts/smoke-prod.sh
#
# Exit code = number of failed checks (0 = all good). Every check maps to a real past incident.
set -u

BASE=${BASE:-https://be.osir.com}
KC=${KC:-https://auth.osir.com/realms/osir}
CLIENT=${CLIENT:-mcp-client}
MIN_TOOLS=${MIN_TOOLS:-90}
ACCEPT='Accept: application/json, text/event-stream'
JSON='Content-Type: application/json'
fails=0

pass() { printf '  ok    %s\n' "$1"; }
fail() { printf '  FAIL  %s  (%s)\n' "$1" "$2"; fails=$((fails + 1)); }
# expect <label> <wanted-status> <curl args...>; body lands in $BODY, headers in $HDRS
expect() {
  local label=$1 want=$2; shift 2
  local out; out=$(curl -s -D - --max-time 20 "$@") || { fail "$label" "curl error"; return 1; }
  HDRS=${out%%$'\r\n\r\n'*}; BODY=${out#*$'\r\n\r\n'}
  local got; got=$(printf '%s' "$HDRS" | awk 'NR==1{print $2}')
  if [ "$got" = "$want" ]; then pass "$label"; else fail "$label" "HTTP $got, want $want"; return 1; fi
}
has() { case "$BODY" in *"$2"*) pass "$1" ;; *) fail "$1" "body lacks $2" ;; esac; }
lacks() { case "$BODY" in *"$2"*) fail "$1" "body contains $2" ;; *) pass "$1" ;; esac; }

echo "No sign-in URL ($BASE/mcp/http)"
INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"smoke-prod","version":"1"}}}'
if expect "initialize without token -> 200" 200 -X POST "$BASE/mcp/http" -H "$JSON" -H "$ACCEPT" -d "$INIT"; then
  SID=$(printf '%s' "$HDRS" | awk 'tolower($1)=="mcp-session-id:"{print $2}' | tr -d '\r')
  SESSION=(); [ -n "$SID" ] && SESSION=(-H "Mcp-Session-Id: $SID")
  curl -s -o /dev/null --max-time 20 -X POST "$BASE/mcp/http" -H "$JSON" -H "$ACCEPT" "${SESSION[@]}" \
       -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'
  if expect "tools/list -> 200" 200 -X POST "$BASE/mcp/http" -H "$JSON" -H "$ACCEPT" "${SESSION[@]}" \
       -d '{"jsonrpc":"2.0","id":2,"method":"tools/list"}'; then
    n=$(printf '%s' "$BODY" | grep -o '"inputSchema"' | wc -l)
    [ "$n" -ge "$MIN_TOOLS" ] && pass "tools/list has $n tools (>= $MIN_TOOLS)" || fail "tool count" "$n < $MIN_TOOLS"
  fi
  if expect "anonymous tool call -> 200" 200 -X POST "$BASE/mcp/http" -H "$JSON" -H "$ACCEPT" "${SESSION[@]}" \
       -d '{"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"checkDomainAvailability","arguments":{"domain":"example.com"}}}'; then
    lacks "checkDomainAvailability works without login" '"isError":true'
  fi
fi
# 2026-09-18 incident: Claude.ai's No sign-in check fetches this; a 401 here blocks every new connector.
expect "PRM for /mcp/http is 404, never 401" 404 "$BASE/.well-known/oauth-protected-resource/mcp/http"

echo "OAuth URL ($BASE/mcp/oauth)"
expect "no token -> 401" 401 -X POST "$BASE/mcp/oauth" -H "$JSON" -H "$ACCEPT" -d "$INIT" &&
  case "$HDRS" in *"resource_metadata=\"$BASE/.well-known/oauth-protected-resource/mcp/oauth\""*) pass "WWW-Authenticate points at /mcp/oauth metadata" ;;
                  *) fail "WWW-Authenticate" "missing or wrong resource_metadata" ;; esac
expect "PRM for /mcp/oauth -> 200" 200 "$BASE/.well-known/oauth-protected-resource/mcp/oauth" &&
  has "PRM resource = /mcp/oauth" "\"resource\":\"$BASE/mcp/oauth\""
expect "root PRM -> 200" 200 "$BASE/.well-known/oauth-protected-resource" &&
  has "root PRM resource = /mcp/http" "\"resource\":\"$BASE/mcp/http\""
expect "AS metadata -> 200" 200 "$BASE/.well-known/oauth-authorization-server" && {
  has "AS issuer = Keycloak realm" "\"issuer\":\"$KC\""
  lacks "AS metadata does not advertise DCR" 'registration_endpoint'
}

echo "Keycloak ($KC, client $CLIENT)"
auth() { printf '%s' "$KC/protocol/openid-connect/auth?client_id=$CLIENT&response_type=code&scope=openid&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&redirect_uri=$1"; }
for cb in https://claude.ai/api/mcp/auth_callback https://claude.com/api/mcp/auth_callback https://chatgpt.com/connector_platform_oauth_redirect; do
  expect "redirect accepted: $cb" 200 "$(auth "$cb")"
done
expect "foreign redirect refused" 400 "$(auth https://smoke-test.invalid/cb)"
expect "password grant refused (Direct access grants off)" 400 -X POST "$KC/protocol/openid-connect/token" \
  -d "grant_type=password&client_id=$CLIENT&username=smoke-test-nonexistent&password=x" &&
  has "  ...as unauthorized_client" 'unauthorized_client'
# 2026-09-15 incident: anonymous DCR was open. If it ever succeeds again, delete the client at once.
if ! expect "anonymous client registration refused" 403 -X POST "$KC/clients-registrations/openid-connect" -H "$JSON" \
     -d '{"client_name":"smoke-test","redirect_uris":["https://smoke-test.invalid/cb"]}'; then
  uri=$(printf '%s' "$BODY" | grep -o '"registration_client_uri":"[^"]*"' | cut -d'"' -f4)
  tok=$(printf '%s' "$BODY" | grep -o '"registration_access_token":"[^"]*"' | cut -d'"' -f4)
  if [ -n "$uri" ] && [ -n "$tok" ]; then
    curl -s -o /dev/null -w '        cleanup: deleted probe client (HTTP %{http_code})\n' -X DELETE "$uri" -H "Authorization: Bearer $tok"
  fi
fi

echo "Other"
expect "MCP health -> 200" 200 "$BASE/mcp/health"
expect "A2A agent card -> 200" 200 "$BASE/.well-known/agent.json"

echo; [ "$fails" -eq 0 ] && echo "All checks passed." || echo "$fails check(s) FAILED."
exit "$fails"
