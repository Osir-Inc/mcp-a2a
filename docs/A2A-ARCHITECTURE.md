# OSIR Agent-to-Agent (A2A) Architecture

## How MCP and A2A Complement Each Other

- **MCP** is tool-level: lets a single agent discover and call 71 tools. The "hands" of an agent.
- **A2A** is task-level: lets agents discover each other, delegate tasks, and exchange results. The "organizational layer."

Each specialist agent is an A2A server that internally uses the existing `common` services (same ones the MCP server uses). The orchestrator discovers specialists via A2A Agent Cards and sends them tasks.

```
User Request
    |
    v
[Orchestrator Agent]  <-- A2A client
    |         |         |
    v         v         v
[Domain     [DNS      [Billing    <-- A2A servers (specialist agents)
 Agent]      Agent]    Agent]
    |         |         |
    v         v         v
[common/ - 12 services]           <-- Shared service layer
    |
    v
[OSIR Backend API - 403 endpoints]
```

Both MCP and A2A are "front doors" to the same `common` module. MCP serves single-agent clients (Claude Desktop). A2A adds orchestration for complex multi-step workflows.

## Specialist Agents

| Agent | A2A Skills | Tools Used |
|-------|-----------|------------|
| **Orchestrator** | `plan_workflow`, `decompose_task`, `coordinate` | None directly (delegates) |
| **Domain Agent** | `search_domains`, `register_domain`, `manage_domain`, `transfer_domain` | Domain (12) + Suggestion (7) + Transfer (5) + Host (4) |
| **DNS Agent** | `configure_dns`, `manage_records` | DNS (5) |
| **Infrastructure Agent** | `provision_vps`, `manage_vps` | VPS (10) + Catalog (3) |
| **Billing Agent** | `check_balance`, `manage_invoices`, `process_payment` | Billing (9) |
| **Contact Agent** | `manage_contacts`, `assign_contacts` | Contact (6) |
| **Account Agent** | `manage_account`, `view_audit` | Account (2) + Audit (3) + Auth (5) |

## New Module Structure

```
com.osir.agent/
├── common/          # Existing: services, clients, models
├── mcp-server/      # Existing: MCP tool endpoints at /mcp
├── a2a-server/      # NEW: A2A protocol layer + orchestrator
│   ├── protocol/    # AgentCard, A2ATask, A2AMessage, TaskStore
│   ├── agents/      # BaseSpecialistAgent + 6 specialists
│   ├── orchestrator/# OrchestratorAgent, TaskPlanner, WorkflowEngine
│   ├── resources/   # JAX-RS: /a2a, /.well-known/agent.json
│   └── auth/        # A2AAuthBridge (uses AuthService.restoreSession)
└── settings.gradle  # include 'common', 'mcp-server', 'a2a-server'
```

## A2A Protocol Details

### Agent Discovery
Each agent publishes `/.well-known/agent.json`:
```json
{
  "name": "OSIR Domain Agent",
  "description": "Manages domain registration, transfers, suggestions, and host records",
  "url": "https://agent.osir.com/a2a/domain",
  "version": "1.0.0",
  "capabilities": { "streaming": true, "pushNotifications": true },
  "skills": [
    { "id": "register_domain", "name": "Register a new domain" },
    { "id": "search_domains", "name": "Search and suggest domain names" }
  ],
  "authentication": { "schemes": ["bearer"] }
}
```

### Task Lifecycle
```
submitted -> working -> [input-required] -> completed | failed | canceled
```

### JSON-RPC 2.0 at POST /a2a
Methods: `tasks/send`, `tasks/get`, `tasks/cancel`, `tasks/sendSubscribe` (SSE streaming)

## Example Workflow

**User request:** "Set up a new client with 5 domains, DNS, VPS, and billing"

```
1. Contact Agent: create contact record (registrant info)
2. Domain Agent: register 5 domains (parallel, needs step 1)
3. DNS Agent: configure DNS for each domain (parallel, needs step 2)
4. Infrastructure Agent: provision VPS (parallel with step 3)
5. Billing Agent: verify balance, generate invoices (needs steps 2+4)
```

The orchestrator's `TaskPlanner` decomposes this using Ollama (qwen2.5:14b), and `WorkflowEngine` executes the DAG.

## Authentication Bridge

A2A tasks carry the user's auth token in metadata. `A2AAuthBridge` calls `AuthService.restoreSession()` to establish context before each specialist agent executes.

**Critical refactor needed:** `AuthService.currentSessionId` is a singleton. For multi-tenant A2A, this must become task-scoped or use `RequestScoped` context propagation.

## Phased Implementation

### Phase 1: A2A Protocol Foundation (2-3 weeks)
- Create `a2a-server` Gradle module
- Implement protocol models (AgentCard, A2ATask, A2AMessage)
- Implement A2AResource (POST /a2a with JSON-RPC 2.0)
- Implement AgentCardResource (GET /.well-known/agent.json)
- Implement TaskStore (in-memory ConcurrentHashMap)
- Write BaseSpecialistAgent + Domain Agent as first specialist
- Deliverable: Domain Agent responds to A2A tasks

### Phase 2: All Specialist Agents (2 weeks)
- Implement remaining 5 specialist agents
- Implement A2AAuthBridge
- Test each agent with direct JSON-RPC calls

### Phase 3: Orchestrator (2-3 weeks)
- AgentRegistry (discovers agents)
- TaskPlanner (LLM-powered decomposition via Ollama)
- WorkflowEngine (DAG execution, input-required handling)
- OrchestratorResource (user-facing endpoint)

### Phase 4: Streaming & Push Notifications (1-2 weeks)
- SSE streaming for long-running tasks
- Push notification callbacks for async operations (transfers)

### Phase 5: External A2A Federation (1-2 weeks)
- Public agent cards
- A2A authentication (client credentials for agent-to-agent)
- Go CLI integration via CliExecutor for edge cases

## Key Risks

1. **Auth singleton** - `AuthService.currentSessionId` must be refactored for multi-tenant
2. **Task durability** - ConcurrentHashMap loses state on restart (consider Redis for prod)
3. **LLM dependency** - Orchestrator depends on Ollama; add rule-based fallback for common workflows
4. **A2A spec maturity** - Released April 2025, still evolving; core primitives are stable
