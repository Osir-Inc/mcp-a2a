#!/usr/bin/env bash
# Closes anonymous Dynamic Client Registration on the osir realm (it reopened once already, see
# smoke-prod.sh). Needs a whitelisted IP for the admin API (WSL here) and a master-realm admin.
#
#   ./scripts/keycloak-close-anonymous-dcr.sh          # show state, ask before changing anything
#
# Fix = the anonymous "trusted-hosts" registration policy with an EMPTY host list, which makes
# Keycloak answer anonymous registrations with 403 "Host not trusted". Nothing of ours uses DCR.
set -eu
KC=${KC:-https://auth.osir.com}
REALM=${REALM:-osir}
TYPE=org.keycloak.services.clientregistration.policy.ClientRegistrationPolicy

read -rp "Keycloak admin username: " U
read -rsp "Password: " P; echo
TOKEN=$(curl -s -d grant_type=password -d client_id=admin-cli --data-urlencode "username=$U" --data-urlencode "password=$P" \
  "$KC/realms/master/protocol/openid-connect/token" | jq -r .access_token)
unset P
[ -n "$TOKEN" ] && [ "$TOKEN" != null ] || { echo "Admin login failed."; exit 1; }
api() { curl -s -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" "$@"; }

echo; echo "== Anonymous client-registration policies now:"
POLICIES=$(api "$KC/admin/realms/$REALM/components?type=$TYPE")
echo "$POLICIES" | jq '.[] | select(.subType=="anonymous") | {id, name, providerId, config}'

TH=$(echo "$POLICIES" | jq -r '[.[] | select(.subType=="anonymous" and .providerId=="trusted-hosts")][0].id // empty')
CONFIG='{"trusted-hosts":[],"host-sending-registration-request-must-match":["true"],"client-uris-must-match":["true"]}'

echo; echo "== Clients with generated (UUID) client ids, usually created through DCR:"
api "$KC/admin/realms/$REALM/clients?max=500" |
  jq -r '.[] | select(.clientId|test("^[0-9a-f]{8}-[0-9a-f]{4}-")) | "\(.id)  clientId=\(.clientId)  name=\(.name // "-")  redirects=\(.redirectUris|join(","))"'

echo
if [ -n "$TH" ]; then
  echo "Plan: set Trusted Hosts policy $TH to an empty host list."
else
  echo "Plan: Trusted Hosts policy is MISSING; create it with an empty host list."
fi
read -rp "Apply? [y/N] " ok; [ "$ok" = y ] || { echo "Nothing changed."; exit 0; }

BACKUP=$HOME/kc-registration-policies-$(date +%Y%m%d-%H%M%S).json
echo "$POLICIES" > "$BACKUP"; echo "Backup of all registration policies: $BACKUP"

if [ -n "$TH" ]; then
  echo "REVERT: PUT the old config of $TH back from $BACKUP."
  api "$KC/admin/realms/$REALM/components/$TH" | jq --argjson c "$CONFIG" '.config = $c' |
    api -X PUT "$KC/admin/realms/$REALM/components/$TH" -d @- -o /dev/null -w "PUT policy: HTTP %{http_code}\n"
else
  RID=$(api "$KC/admin/realms/$REALM" | jq -r .id)
  jq -n --arg p "$RID" --arg t "$TYPE" --argjson c "$CONFIG" \
     '{name:"Trusted Hosts", providerId:"trusted-hosts", providerType:$t, parentId:$p, subType:"anonymous", config:$c}' |
    api -X POST "$KC/admin/realms/$REALM/components" -d @- -D /tmp/kc-post.h -o /dev/null
  head -1 /tmp/kc-post.h
  NEW=$(awk 'tolower($1)=="location:"{print $2}' /tmp/kc-post.h | tr -d '\r' | sed 's|.*/||')
  echo "Created Trusted Hosts policy id: $NEW"
  echo "REVERT (reopens anonymous registration exactly as before), with a fresh admin token in \$TOKEN:"
  echo "  curl -X DELETE -H \"Authorization: Bearer \$TOKEN\" $KC/admin/realms/$REALM/components/$NEW"
fi

echo
echo "Verify: ./scripts/smoke-prod.sh  ('anonymous client registration refused' must be ok; it cleans up after itself)."
echo "Leftover DCR clients listed above: review them, then delete in the admin UI"
echo "(Clients -> the client -> Action -> Delete). Never delete mcp-client or osir-cli."
