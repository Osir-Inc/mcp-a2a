# OSIR A2A Server — API Reference

## Transport

- **Endpoint:** `POST /a2a`
- **Streaming:** `POST /a2a/stream` (SSE)
- **Protocol:** JSON-RPC 2.0
- **Content-Type:** `application/json`
- **Authentication:** `Authorization: Bearer <token>` header

---

## Methods

### tasks/send

Execute a task or continue a multi-turn conversation.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "method": "tasks/send",
  "params": {
    "id": "task-id",
    "skill": "check_availability",
    "agent": "domain-agent",
    "refreshToken": "optional-keycloak-refresh-token",
    "webhookUrl": "https://your-server.com/webhook",
    "message": {
      "role": "user",
      "parts": [
        { "type": "text", "text": "Check if example.com is available" }
      ]
    }
  }
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `params.id` | No | Task ID. Auto-generated UUID if omitted. Reuse to continue a multi-turn task. |
| `params.skill` | No | Explicit skill ID for deterministic routing (score = 1.0). |
| `params.agent` | No | Explicit agent ID for deterministic routing (score = 1.0). |
| `params.refreshToken` | No | KeyCloak refresh token. If the access token is expired, the server refreshes via KeyCloak and returns new tokens. |
| `params.webhookUrl` | No | URL to POST task result when complete/failed. 3 retries with exponential backoff. |
| `params.message` | **Yes** | The user's message. Must have `role` ("user") and `parts` array. |
| `params.message.parts[].type` | **Yes** | `"text"` or `"data"`. |
| `params.message.parts[].text` | For text | The text content. Max 10,000 characters. |

**Response (success):**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "result": {
    "id": "task-id",
    "status": "completed",
    "history": [
      { "role": "user", "parts": [{"type": "text", "text": "..."}] },
      { "role": "agent", "parts": [{"type": "text", "text": "Domain is available"}] }
    ],
    "artifacts": [
      {
        "name": "availability-result",
        "parts": [{ "type": "data", "data": { "domain": "example.com", "available": true } }]
      }
    ],
    "metadata": {
      "tokenExpiresIn": 120,
      "token": { "accessToken": "Bearer ...", "refreshToken": "..." }
    },
    "createdAt": "2026-04-08T10:00:00Z",
    "updatedAt": "2026-04-08T10:00:01Z"
  }
}
```

**Response metadata fields (conditional):**

| Field | When present | Description |
|-------|-------------|-------------|
| `metadata.tokenExpiresIn` | Access token expires in < 5 minutes | Seconds until token expiry. Client should refresh proactively. |
| `metadata.token.accessToken` | Token was refreshed via refreshToken | New bearer token for subsequent requests. |
| `metadata.token.refreshToken` | Token was refreshed via refreshToken | New refresh token (use for next refresh). |

---

### tasks/get

Retrieve the current state of a task.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "method": "tasks/get",
  "params": { "id": "task-id" }
}
```

**Response:** Same as `tasks/send` result.

---

### tasks/cancel

Cancel a non-terminal task.

**Request:**
```json
{
  "jsonrpc": "2.0",
  "id": "request-id",
  "method": "tasks/cancel",
  "params": { "id": "task-id" }
}
```

**Response:** The canceled task with `status: "canceled"`.

---

## Task Lifecycle

```
submitted  ──>  working  ──>  completed
                         ──>  failed
                         ──>  input-required  ──>  (send more info, same task ID)  ──>  working  ──>  ...
                         ──>  canceled
```

| Status | Meaning | Terminal? |
|--------|---------|-----------|
| `submitted` | Task received, not yet routed | No |
| `working` | Agent is executing | No |
| `input-required` | Agent needs more information from the user | No |
| `completed` | Task finished successfully | Yes |
| `failed` | Task failed (error in artifacts/history) | Yes |
| `canceled` | Task was canceled via tasks/cancel | Yes |

---

## Error Codes

| Code | Name | Description |
|------|------|-------------|
| `-32700` | Parse Error | Malformed JSON |
| `-32600` | Invalid Request | Missing jsonrpc version or method |
| `-32601` | Method Not Found | Unknown method (not tasks/send, tasks/get, tasks/cancel) |
| `-32602` | Invalid Params | Missing params, message, or message too long |
| `-32603` | Internal Error | Server error (details logged, not returned) |
| `-32001` | Task Not Found | No task with the given ID |
| `-32002` | Task Not Cancelable | Task is already terminal or doesn't exist |
| `-32003` | Token Expired | Access token expired. Provide refreshToken or re-authenticate. |

---

## SSE Streaming

`POST /a2a/stream` accepts the same request format as `tasks/send` but returns Server-Sent Events.

Each event is a JSON-RPC response showing the task's current state:

```
data: {"jsonrpc":"2.0","id":"1","result":{"status":"submitted",...}}

data: {"jsonrpc":"2.0","id":"1","result":{"status":"working",...}}

data: {"jsonrpc":"2.0","id":"1","result":{"status":"completed",...}}
```

The stream closes after the final state is emitted.

---

## Agent Discovery

### GET /.well-known/agent.json

Returns the platform agent card (aggregated skills from all agents):

```json
{
  "name": "OSIR Agent Platform",
  "description": "AI-powered domain registrar...",
  "url": "/a2a",
  "version": "1.0.0",
  "capabilities": { "streaming": false, "pushNotifications": false },
  "authentication": { "schemes": ["bearer"] },
  "skills": [ ... ]
}
```

### GET /.well-known/agents

Returns an array of all specialist agent cards:

```json
[
  { "name": "OSIR Domain Agent", "skills": [...] },
  { "name": "OSIR DNS Agent", "skills": [...] },
  ...
]
```

---

## Agents & Skills

### Domain Agent (`domain-agent`)

| Skill ID | Description |
|----------|-------------|
| `check_availability` | Check domain availability |
| `register_domain` | Register a new domain |
| `get_domain_info` | Get domain details (expiry, nameservers, status) |
| `list_domains` | List all user's domains |
| `renew_domain` | Renew a domain for one year |
| `lock_domain` | Enable registrar lock |
| `unlock_domain` | Remove registrar lock |
| `suggest_domains` | Generate domain name suggestions |
| `transfer_domain` | Transfer from another registrar (requires auth code) |
| `enable_privacy` | Enable WHOIS privacy protection |
| `disable_privacy` | Disable WHOIS privacy protection |
| `enable_autorenew` | Enable automatic renewal |
| `disable_autorenew` | Disable automatic renewal |

### DNS Agent (`dns-agent`)

| Skill ID | Description |
|----------|-------------|
| `list_dns_records` | List all DNS records for a domain |
| `create_dns_record` | Create A, AAAA, CNAME, MX, TXT, or SRV record |
| `update_dns_record` | Update an existing record |
| `delete_dns_record` | Delete a record by ID |
| `get_dns_record` | Get details of a specific record |

### VPS Agent (`vps-agent`)

| Skill ID | Description |
|----------|-------------|
| `list_vps_packages` | List available VPS plans |
| `list_vps_locations` | List datacenter locations |
| `order_vps` | Provision a new VPS instance |
| `list_vps_instances` | List user's active VPS instances |
| `get_vps_details` | Get details of a specific instance |
| `delete_vps` | Terminate a VPS instance |
| `vps_panel_login` | Get control panel login URL |
| `get_catalog` | Get the full product catalog |

### Billing Agent (`billing-agent`)

| Skill ID | Description |
|----------|-------------|
| `get_balance` | Check account balance |
| `list_invoices` | List all invoices |
| `get_invoice` | Get details of a specific invoice |
| `pay_invoice` | Pay an invoice from account balance |
| `invoice_statistics` | Get invoice summary statistics |
| `create_payment` | Create Stripe checkout session to add funds |
| `get_transactions` | View payment history |
| `preview_fees` | Preview fees for a payment amount |
| `get_domain_pricing` | Get pricing for domain extensions |

### Contact Agent (`contact-agent`)

| Skill ID | Description |
|----------|-------------|
| `list_contacts` | List all contacts |
| `get_contact` | Get a specific contact's details |
| `create_contact` | Create a new contact record |
| `update_contact` | Update an existing contact |
| `delete_contact` | Delete a contact |
| `get_domain_contacts` | Get contacts assigned to a domain |

### Account Agent (`account-agent`)

| Skill ID | Description |
|----------|-------------|
| `get_profile` | Get user profile information |
| `get_account_summary` | Get comprehensive account overview |
| `get_auth_status` | Check authentication status |
| `get_audit_logs` | View audit logs |
| `get_domain_audit` | View audit trail for a specific domain |
| `get_recent_activity` | View recent activity across services |

### Orchestrator (`orchestrator`)

| Skill ID | Description |
|----------|-------------|
| `orchestrate` | Decompose and execute a multi-step workflow (max 15 steps) |
| `plan_workflow` | Create an execution plan without executing |

---

## Routing

Tasks are routed to agents by **scoring**. Each agent scores the task 0.0–1.0. The highest scorer wins.

**Explicit routing (deterministic, score = 1.0):**
- Set `params.skill` to a skill ID → routes to the agent that owns that skill
- Set `params.agent` to an agent ID → routes directly to that agent

**Intent-based routing (automatic):**
- The message text is scored against each agent's keyword set
- Domain-related words route to the Domain Agent, DNS keywords to the DNS Agent, etc.
- The orchestrator scores highest when the message mentions multiple service areas

**Recommendation:** Use explicit `skill` + `agent` for programmatic clients. Use intent-based for natural language interfaces.

---

## Webhook Push Notifications

Set `params.webhookUrl` when creating a task. When the task reaches a terminal state (completed, failed, canceled), the server POSTs to the URL:

```json
{
  "taskId": "task-id",
  "status": "completed",
  "artifacts": [...]
}
```

Retries: 3 attempts with exponential backoff (1s, 2s, 4s). Timeout: 10s per attempt.

---

## Rate Limits

| Limit | Default | Env var |
|-------|---------|---------|
| Global concurrent | 50 | `A2A_RATE_LIMIT_GLOBAL` |
| Per-user concurrent | 10 | `A2A_RATE_LIMIT_PER_USER` |

When exceeded, returns HTTP 429 with:
```json
{"jsonrpc":"2.0","error":{"code":-32000,"message":"Too many concurrent requests..."}}
```

---

## Request Signing (Optional)

When `A2A_SIGNING_SECRET` is configured, clients can sign requests:

**Headers:**
- `X-Signature`: HMAC-SHA256 of `path:timestamp` encoded as URL-safe Base64
- `X-Timestamp`: Unix epoch seconds

**Verification:** The server rejects requests with invalid signatures or timestamps older than 5 minutes.

Set `A2A_SIGNING_REQUIRED=true` to reject all unsigned requests.
