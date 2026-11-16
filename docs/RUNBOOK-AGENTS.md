# Runbook: AI agents (Hermes) on the customer's own VPS

Operator and developer runbook for the `osirAgent*` tools (shipped in v2.5.0). Read this before
changing, releasing, enabling or debugging them. The customer-facing how-to for osir.com is
`docs/osir-com/run-hermes-on-your-vps.js`.

Design: `app.osir.deploy` → `docs/spec_mcp_hermes_and_image_apps.md` (Part 3) and
`docs/howto_agents.md`. Deploy-backend API: `app.osir.deploy/CONTRACTS.md` §2.

## 1. What it is

A customer says "install Hermes on my VPS" in Claude (or any MCP client). OSIR installs **Hermes Agent**
(Nous Research's self-hosted AI agent with a web dashboard) on a VPS **the customer owns**, behind HTTPS
with a password-protected dashboard. Later they say "connect it to DeepSeek" or "reset the password".

- An agent is a separate product from `osirApp*`: the customer never sees or edits its code, only its
  settings. Underneath it is a deploy-backend app with `kind: "agent"`, `catalog: "hermes"`.
- Nothing runs on OSIR's shared servers. The agent is built and run on the customer's VPS.
- Hermes needs a VPS with **at least 4 GB RAM** (`AgentService.MIN_MEMORY_MB`).

## 2. Who owns what

| Part | Where | What it does |
|---|---|---|
| MCP tools | this repo: `AgentMCPServer`, `AgentService` | The 7 tools, VPS selection/ordering, naming, DNS, refusals, password handling |
| Shared VPS steps | this repo: `MoveToOwnedService` | Attach a VPS the customer owns, or order one behind the confirmation gate, wait for the OS build, bind DNS. Shared with `osirAppDeployToVps`; only the final call differs |
| Backend calls + rules | this repo: `DeploymentService` (common) | `POST /v1/agents`, `/providers`, `/password`; `refuseIfAgent`; reserved secret keys |
| Install on the box | deploy backend (C2, `be-deploy.osir.com`, repo `app.osir.deploy`) | Pins the Hermes image, generates the password, installs on the VPS, tracks it in `status.ownedMove` |
| VPS, DNS, billing | OSIR backend (`be.osir.com`, repo `domain-registrar`) | Unchanged; called exactly as for "move my app to my VPS" |

## 3. The tools

| Tool | Backend call | Gated | Notes |
|---|---|---|---|
| `osirAgentDeploy(agent, instanceId? \| packageId?, domain?, username?)` | VPS steps, then `POST /v1/agents` | `packageId` only (FINANCIAL) | Picks the name itself (`hermes-1`, `hermes-2`…). Returns URL, username, **password once** |
| `osirAgentList()` | `GET /v1/apps`, keep `kind: agent` | no | |
| `osirAgentStatus(agentId)` | `GET /v1/apps/{id}/status` + live GET of the URL | no | `installed`, `dashboardProtected` (401 = true, 2xx = false, else null) |
| `osirAgentConfigure(agentId, provider, apiKey)` | `POST /v1/agents/{id}/providers` | no | deepseek, openai, openrouter, anthropic. Agent reinstalls with the key |
| `osirAgentLogs(agentId)` | none | no | Says "not available yet" until C2 has owned-box logs |
| `osirAgentResetPassword(agentId)` | `POST /v1/agents/{id}/password` | no | New password **once**; the only way to change the login |
| `osirAgentDelete(agentId)` | `DELETE /v1/apps/{id}` + execute | DESTRUCTIVE | Container removed; `/data` stays on the VPS; VPS not cancelled |

### How `osirAgentDeploy` decides what to do (`AgentService.deployWithoutSpending`)

1. Unknown agent → refusal listing the catalog.
2. **`instanceId` given:** VPS under 4 GB → refused, nothing done. An agent already on that VPS →
   installed: report it; MOVING: report progress, send nothing; FAILED: re-send (C2 resumes, no new
   password). Otherwise install with a new free name.
3. **No `instanceId`**, in order: an order still tracked in memory → resume it; a VPS named
   `<agent>-N-owned.osir.app` with no agent → install on it; an existing agent of this kind (read from
   C2, so it survives restarts) → resume it if unfinished, report it if finished.
4. Only if none of that applies and `packageId` is given → stage a VPS order (confirmation). At execute
   time it re-runs step 3 first, so a second confirmation never buys a second VPS.

Ordering a new VPS for an agent is only possible when `OSIR_AGENTS_ORDERABLE` lists it (see §5).

## 4. Rules that must hold (each has a test)

| Rule | Where | Test |
|---|---|---|
| The dashboard password appears only in the `POST /v1/agents` and `/password` responses, goes to the user once in the tool result, and is never logged, stored, sent to telemetry or GlitchTip | `AgentDtos` (redacted `toString`), `DeploymentService.agentCall` (logs no bodies) | `AgentServiceTest.install_onOwnedBox_returnsThePasswordOnce_…`, `resetPassword_…` |
| A retry returns `password: null` → "installation resumed", never "here is your password" | `AgentService.ship` | `retry_thatResumes_neverClaimsToShowAPassword` |
| Every `osirApp*` tool refuses `kind: agent` and points to `osirAgent*` | `DeploymentService.refuseIfAgent` (deploy, logs, secrets, database, source); tool layer for status, deployToVps, delete | `DeploymentServiceAgentTest.everyAppPathRefusesAnAgent_…` |
| `osirAppSetSecret` refuses `HERMES_DASHBOARD_*` | `DeploymentService.setSecret` (shared, so A2A is covered too) | `reservedDashboardKeys_…` |
| A retry never buys a second VPS or restarts a running install | `AgentService.deployWithoutSpending`, `orderAndInstall` | `retryWithoutInstanceId_…`, `aSecondConfirmedOrder_…`, `anInstallStillRunning_…` |
| Ordering and deleting go through the confirmation gate | `AgentMCPServer` (`pendingActionStore.stage`) | `ConfirmationGateInvariantTest` (GATED set) |
| Agent tools are MCP-only, not A2A | nothing in `a2a-server` references them | review on every A2A change |

**Why MCP-only:** A2A has no real confirmation step for spending or deleting, and the one-time
password would go to another agent instead of the user. `osirAgentList`/`osirAgentStatus` could be
added to A2A safely; deploy-with-`packageId` and delete need the A2A confirmation design first.

## 5. Release and switch-on

Hermes goes live in three steps, in this order.

**1. Deploy backend releases Hermes** (`app.osir.deploy/docs/howto_agents.md` §1: roll out, one real
test run on a 4 GB+ test VPS, pin the tested image digest). Until then `POST /v1/agents` answers
**503 "not available yet"** and `osirAgentDeploy` relays it plainly. Nothing is installed.

**2. Open VPS ordering for the agent** (MCP server `.env`, then restart the MCP container):

```
OSIR_AGENTS_ORDERABLE=hermes
```

Why this exists: C2 has no read-only way to ask whether an agent is released, and its 503 only arrives
after the VPS is bought. Leave it empty until step 1 is done. Installing on a VPS the customer already
owns works without it. Remove this setting once C2 offers an availability endpoint.

**3. Verify** (§6).

To ship a code change to these tools, follow `.claude/skills/osir-release-check`: bump the version in
all 8 places, clean build, tag, hand over. Docker build and deploy are done by hand by the owner.

## 6. Verify

### Read-only, no account (after every deploy)

```bash
curl -s -X POST https://be.osir.com/mcp/http -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"x","version":"1"}}}' \
  | grep -o '"serverInfo":{[^}]*}'                       # version == the release
```

Then `tools/list` on the same session (echo the `Mcp-Session-Id` header) and parse it as JSON, never
with grep:

- 113 tools in `result.tools`;
- the 7 `osirAgent*` tools exist; `osirAgentDeploy` requires only `agent`; `osirAgentDelete` has
  `destructiveHint: true`;
- `initialize` → `instructions` contains "use osirAgentDeploy - never osirAppDeploy".

Verify against the **live** descriptions, never the source.

### Functional smoke test (test account, after step 1 of §5)

Run in Claude with the OSIR connector, on a test account that owns a 4 GB+ test VPS:

| Say | Expect |
|---|---|
| "Install Hermes on my VPS" | Claude lists VPSs, calls `osirAgentDeploy(hermes, instanceId)`, shows URL, username and password once |
| "Is my Hermes ready?" (repeat) | `osirAgentStatus`: installing → installed, `dashboardProtected: true` |
| Open the URL in a private window | Login prompt; the username + password work |
| "Connect my Hermes to DeepSeek" + key | `osirAgentConfigure`; status goes back to installing, then installed; a chat in the dashboard gets an answer |
| "Reset my Hermes password" | New password shown once; the old one stops working |
| "Set HERMES_DASHBOARD_BASIC_AUTH_PASSWORD on hermes-1" | Refused, points to `osirAgentResetPassword` |
| "Show the status of hermes-1 with osirAppStatus" | Refused, points to `osirAgentStatus` |
| "Remove my Hermes" | Staged; after confirming, the container is gone; `/data` remains on the VPS |

## 7. Troubleshooting

| Symptom (what the customer or model sees) | Cause | Fix |
|---|---|---|
| "Hermes is not available on Osir yet" | C2 503: image not pinned (§5 step 1 not done) | Release on the deploy backend |
| "Ordering a new VPS for Hermes is not open yet" | `OSIR_AGENTS_ORDERABLE` does not list `hermes` | Set it (§5 step 2), or install on a VPS the customer owns |
| "VPS … has N MB of memory; Hermes needs at least 4096 MB" | VPS or package under 4 GB | Bigger VPS |
| Status: install failed, `BOX_TOO_SMALL` | C2 measured the box under 4 GB | Bigger VPS |
| Status: `BOX_KEY_REFUSED` | The VPS rejects the platform deploy key | Customer adds the key shown in the message to `/root/.ssh/authorized_keys`, then retries with the same `instanceId` |
| Status: `BOX_PORTS_IN_USE` | Another web server holds 80/443 | Customer stops it (`systemctl disable --now nginx`), then retries |
| Status: `APP_START_FAILED` | Image pull/build/start failed on the box | Deploy-backend side: `docker logs app-<domain with dashes>` on the VPS; fix in the bundle, not the platform |
| `dashboardProtected: false` | The dashboard answered a page without a login | Treat as an incident: reset the password, check the bundle on the box |
| `dashboardProtected` missing | Dashboard did not answer (restarting, 502) | Check again in a minute; not a fault |
| "Installation resumed … password was shown when first created" | A retry; C2 never returns the password twice | `osirAgentResetPassword` if the customer lost it |
| An `osirApp*` tool says "… is a Hermes agent, not an app" | By design | Use the `osirAgent*` tool it names |

Re-sending the same `osirAgentDeploy` is always safe: it resumes, never creates a new password, never
buys a second VPS.

## 8. Tests

```
common:      DeploymentServiceAgentTest
mcp-server:  AgentServiceTest, MoveToOwnedServiceTest, ConfirmationGateInvariantTest
```

The Gradle test worker wedges on the main dev machine. If `./gradlew test` hangs, run the classes with
the JUnit platform launcher on the module's test classpath (see the project memory note on the wedge).

## 9. Open items

- **Availability endpoint on C2** (`GET` "is agent X released?"), so `OSIR_AGENTS_ORDERABLE` can go.
- **Logs from the customer's VPS**: when C2 has them, `osirAgentLogs` calls them, with redaction
  (Hermes logs contain conversation text).
- **A2A**: read-only agent tools can be added; spending/deleting need the A2A confirmation design.
- **More agents** (OpenClaw): a catalog entry on C2 plus `AgentService.CATALOG`; no new tool.
