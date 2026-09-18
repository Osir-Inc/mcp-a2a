#!/usr/bin/env bash
# Lists OAuth logins Keycloak rejected, grouped by client + redirect URI, so a platform whose
# callback isn't in mcp-client's Valid Redirect URIs (or that uses an unknown client id) shows up.
#
# Run on the Keycloak host:
#   ./check-oauth-redirects.sh              # last 24h of `docker logs keycloak_osir`
#   SINCE=7d KC_CONTAINER=kc ./check-oauth-redirects.sh
#   ./check-oauth-redirects.sh < saved.log  # or any log piped in
#
# Relies on Keycloak's default jboss-logging event listener, which logs errors at WARN:
#   ... type="LOGIN_ERROR", ..., clientId="mcp-client", ..., error="invalid_redirect_uri", redirect_uri="https://..."
set -eu  # no pipefail: grep finding nothing is the good outcome

if [ -t 0 ]; then
  docker logs --since "${SINCE:-24h}" "${KC_CONTAINER:-keycloak_osir}" 2>&1
else
  cat
fi | grep -E 'error="(invalid_redirect_uri|client_not_found|invalid_client|unauthorized_client)"' \
   | awk '
       function field(name,   m) { return match($0, name "=\"[^\"]*\"") ? substr($0, RSTART + length(name) + 2, RLENGTH - length(name) - 3) : "-" }
       { n[field("error") "\t" field("clientId") "\t" field("redirect_uri")]++ }
       END {
         if (length(n) == 0) { print "No rejected OAuth logins found."; exit }
         printf "%-6s %-22s %-28s %s\n", "COUNT", "ERROR", "CLIENT", "REDIRECT_URI"; fflush()
         for (k in n) { split(k, f, "\t"); printf "%-6d %-22s %-28s %s\n", n[k], f[1], f[2], f[3] | "sort -k1,1nr" }
       }'
