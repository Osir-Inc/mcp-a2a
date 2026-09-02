# Backend → MCP handoff (2026-09-01, backend v2.11.0 LIVE)

The backend (Part B) shipped its step 1+2 and the Keycloak realm rework. This file lists exactly
what the MCP/A2A layer must consume, fix, or build against. Backend contracts below are LIVE on
be.osir.com unless marked "next release". Backend status doc:
`domain-registrar/docs/agent-readiness/PLAN-part-b.md`.

## 1. FIX NOW — stop faking availability from 401s (audit F1)

`checkDomainAvailability` currently synthesizes `{available:false, message:"Authentication
required"}` when the backend returns 401. That message is manufactured in THIS codebase — the
backend never produces it. Change:

- Anonymous callers → use the new PUBLIC endpoints (LIVE, no auth):
  - `GET /v1/public/catalog/domains/{domain}/availability` → full `DomainAvailabilityResult`
    (availability + price/fees/promo/premium, byte-identical to the authenticated one)
  - `GET /v1/public/catalog/domains/{domain}/quote?years=N` → `DomainRegistrationQuote`
- Never report `available:false` for a non-domain reason. If a backend call errors, surface the
  error honestly (`{code/errorCode, message/error, resolution}` — see §6).

## 2. FIX NOW — getAuthStatus ignores session keys (user-visible bug)

`getAuthStatus` resolves auth by MCP connection id only, so it reports `authenticated:false`
while a `sessionKey` works fine (confirmed live today). Make it accept the `sessionKey` argument
like every other tool and report that session's status.

## 3. Session lifetime + refresh

- Access tokens are now **1800 s** (was 300). The conversation-session TTL messaging already
  picks this up.
- For sessions longer than Keycloak's SSO idle (12 h): request the `offline_access` scope at
  device login (it's an optional scope on `osir-cli`) → 30-day refresh tokens. Refresh rotation
  is ON (`refreshTokenMaxReuse=2`) — always store the NEW refresh token after each refresh.

## 4. NEW backend capabilities to expose as tools

### 4a. Autonomous onboarding (LIVE) — new tools `createAccount` / `verifyAccount`
- `POST /v1/public/account` (anonymous):
  ```json
  {"email":"…","password":"optional","accountType":"INDIVIDUAL|ORGANIZATION",
   "contact":{"firstName","lastName","email","phone":"+CC.number","street1","city","country":"US", …},
   "agentMetadata":{"name":"…","vendor":"…","principal":"…"},
   "acceptedTerms":true,"termsVersion":"2026-09"}
  ```
  201 → `{accountId, contactId, status:"PENDING_VERIFICATION", verification:{method,sentTo,expiresAt}, nextSteps[]}`
  409 `ACCOUNT_EXISTS` · 400 with exact `{code,message,resolution}` · retry for a PENDING account
  re-sends the verification email (not an error).
- `POST /v1/public/account/verify` `{accountId, code}` → 200 `{status:"ACTIVE", nextSteps}` or
  400 `CODE_EXPIRED` (resolution: re-POST createAccount with the same email).
- The emailed code and the email link are the same token — a principal can relay the code to
  their agent. The contact is the PRINCIPAL's ICANN registrant contact, never the agent.
- Capability matrix: PENDING accounts can search/quote/fund; billable execution returns
  403 `ACCOUNT_NOT_VERIFIED` with a `resolution` — surface it verbatim to the model.

### 4b. Hosting bundle (backend NEXT RELEASE) — new tool `getHostingBundle`
`GET /v1/public/catalog/bundle?domain={domain}` (anonymous) → per-domain hosting offer:
`options.vps.recommended[]` (≤3 cheapest, `price:{amount,currency,taxIncluded,period}`),
`options.mail`, `options.webForwarding`, `options.appDeploy` (builds are FREE; going live =
the VPS price), plus `nextSteps`. This is the business-bet tool: call it after a successful
availability/registration result to make the hosting offer with exact prices, once, honestly.

### 4c. registerDomain — DNS zone now auto-initialises (LIVE)
Registration initialises the PowerDNS zone with OSIR defaults by default (post-commit, async).
New optional request field `initializeDnsZone:false` opts out. Update tool descriptions:
`initializeDnsZone` is no longer a required step after registration (keep the tool for
pre-existing domains).

### 4d. createPaymentSession (backend NEXT RELEASE)
Response gains `pollTool` ("getPaymentTransactions") and `pollEndpoint`
(`/v1/payment/session/{id}`) — poll after handing the checkout URL to the human.

## 5. Telemetry emission (A.7 → backend B.6, backend NEXT RELEASE)

Batch-post session events to `POST /v1/agent/telemetry` (auth: any customer/admin token, or an
API key — role api-user — so ANONYMOUS sessions can be reported too; get one API key issued for
the MCP service). **API keys go in the `X-API-Key` header, NOT `Authorization: Bearer`** — Bearer
is routed to OIDC and 401s. Max 200 events/batch; events without `sessionId` are dropped.
```json
[{"sessionId":"…","clientName":"claude-ai","clientVersion":"…",
  "authMode":"anonymous|device|oauth|client_credentials|api_key",
  "customerId":"…","tenantId":"osir","tool":"registerDomain",
  "stage":"authenticated|quoted|staged|confirmed|registered|deployed",
  "durationMs":420,"staged":true,"confirmed":true,"outcome":"success"}]
```
`stage` drives the funnel dashboard — set it at the moment the session first reaches that stage.

## 6. Error + price shapes (additive, LIVE)

- Backend errors now carry an optional `resolution` (machine-actionable hint) next to
  `error`/`errorCode` (EPPApiResponse) or `code`/`message` (new endpoints). Pass it through.
- Quote DTOs carry an additive normalized `price` object:
  `{amount(cents), currency, taxIncluded, validUntil}` — prefer it over the legacy field zoo.

## 7. OAuth/scopes groundwork (Keycloak LIVE)

- Anonymous DCR works (Claude/ChatGPT custom connectors self-register; anonymous-tier clients
  get realm-default scopes only). Eight scopes exist: `osir:read|domains|dns|apps|vps|mail|billing|account`,
  assigned as OPTIONAL on `mcp-client` and `osir-cli` — start declaring the required scope per
  tool (A.3) and requesting the right scopes at auth.
- PKCE S256 is enforced for all public clients including the device grant (the fix for this is
  already in this repo, deployed).
- A.3 items still open on THIS side: 401 + `WWW-Authenticate: Bearer resource_metadata="…"` on
  unauthenticated protected calls; serving `/.well-known/oauth-protected-resource` (settle with
  backend which deployment owns the path on be.osir.com); Idempotency-Key pass-through once the
  backend ships it (blocked on the ledger work).

## Priority order suggestion
1. §1 + §2 (both are live bugs an agent hits today)
2. §3 refresh + §4a onboarding tools (completes the autonomous E2E path)
3. §4c description updates + §6 error pass-through (cheap)
4. §5 telemetry + §4b bundle tool (when the backend's next release deploys)
5. §7 scope declarations
