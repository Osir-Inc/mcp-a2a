# OpenClaw Skill Guide for OSIR Services

## Overview

This document describes how to create an OpenClaw/ClawHub skill so users can search for, register, and manage domains, VPS, and dedicated servers through the OpenClaw AI agent platform.

**Two complementary approaches:**
1. **MCP-Based Integration** (Primary) - Connect OpenClaw to the existing OSIR MCP server
2. **SKILL.md Skill for ClawHub** (Companion) - Publish a skill that teaches the agent how to use the tools

---

## Approach 1: MCP-Based Integration (Recommended Primary)

The existing MCP server (`OSIR_MCP`) already exposes domain tools like `checkDomainAvailability`, `registerDomain`, `generateDomainSuggestions`, `listUserDomains`, etc. OpenClaw can connect directly via its MCP plugin.

### How It Works

Users configure the MCP server URL in `~/.openclaw/openclaw.json`, and all tools become available automatically via the [openclaw-mcp-plugin](https://github.com/lunarpulse/openclaw-mcp-plugin).

### User Configuration

```json
{
  "plugins": {
    "entries": {
      "mcp-integration": {
        "enabled": true,
        "config": {
          "servers": {
            "osir-registrar": {
              "enabled": true,
              "transport": "http",
              "url": "https://mcp.osir.com/mcp"
            }
          }
        }
      }
    }
  }
}
```

### Transport Requirements

For OpenClaw compatibility, the MCP server should support **Streamable HTTP** transport in addition to SSE. The current SSE transport works with Claude Code, but OpenClaw's MCP plugin uses HTTP POST with JSON-RPC 2.0:

```
POST https://mcp.osir.com/mcp
Content-Type: application/json
mcp-session-id: ...

{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"checkDomainAvailability","arguments":{"domain":"example.com"}}}
```

---

## Approach 2: SKILL.md Skill for ClawHub

A published skill on ClawHub provides the **instructions layer** - teaching the agent *how* to use the tools effectively, handle workflows, and provide good UX.

### Skill Structure

```
osir-registrar/
├── SKILL.md              # Core skill definition
├── claw.json             # ClawHub manifest
├── README.md             # User documentation
├── instructions.md       # Detailed AI instructions
├── src/
│   ├── api-client.ts     # REST API wrapper (optional)
│   └── types.ts          # TypeScript types (optional)
└── examples/
    ├── domain-search.md
    ├── register-domain.md
    └── manage-vps.md
```

### SKILL.md

```yaml
---
name: osir-registrar
description: Search, register and manage domains, VPS and dedicated servers via OSIR.COM registrar services
homepage: https://osir.com
user-invocable: true
metadata: {"openclaw":{"emoji":"🌐","requires":{"env":["OSIR_API_KEY"]},"primaryEnv":"OSIR_API_KEY","os":["darwin","linux","win32"]}}
---

# OSIR Domain & Hosting Management

You are a domain registrar and hosting management assistant powered by the OSIR.COM API.
Your API base URL is configured via the OSIR_API_URL environment variable (default: https://api.osir.com).
Authentication uses the OSIR_API_KEY environment variable as a Bearer token.

## Capabilities

### Domain Services
- **Search & Availability**: Check if domains are available, suggest alternatives, generate name ideas
- **Registration**: Register new domains with contact details, nameservers, and privacy protection
- **Transfer**: Transfer domains from other registrars using EPP/auth codes
- **Management**: Update nameservers, view domain info, check expiration dates
- **Bulk Operations**: Check availability across multiple TLDs, bulk domain suggestions

### VPS Hosting
- **Browse Catalog**: List available VPS packages by location (city/country)
- **Provision**: Order VPS instances with selected packages and locations
- **Manage**: View instance status, reboot, rebuild, upgrade
- **Monitoring**: Check resource usage, bandwidth, uptime

### Dedicated Servers
- **Inventory**: Browse available dedicated server configurations
- **Order**: Provision dedicated servers with OS selection
- **Manage**: Power management, OS reinstall, IPMI access

## API Interaction Rules

1. **Always authenticate first** if the user hasn't already. Use the stored OSIR_API_KEY.
2. **Domain searches**: When a user asks about a domain, first check availability. If unavailable, proactively suggest alternatives.
3. **Registration flow**: Always confirm the following before registering:
   - Domain name and TLD
   - Registration period (years)
   - Contact information (or use account defaults)
   - Nameservers (offer OSIR defaults if none specified)
   - Privacy protection preference
4. **Pricing**: Always show pricing before any purchase operation. Format in USD.
5. **Transfers**: Guide users through the transfer process step by step - they need an EPP/auth code from their current registrar.
6. **VPS/Dedicated**: Always show available locations and let the user choose before provisioning.

## Response Formatting

- Display domain availability results in a clear table format
- Show pricing prominently with currency
- Use status indicators for domain/server states
- Provide next-step suggestions after each operation
- When listing domains, include expiration dates and status

## Error Handling

- If a domain is already registered, suggest alternatives immediately
- If balance is insufficient, show the required amount and how to add funds
- If an API call fails, explain the issue clearly and suggest retry or alternative actions
- For transfer errors, explain common causes (domain locked, auth code expired, etc.)

## Workflow Examples

### "Find me a domain for my coffee shop"
1. Ask for business name or keywords
2. Generate suggestions using bulk domain suggestions API
3. Check availability for top picks
4. Present results with pricing
5. Offer to register if user picks one

### "Transfer mydomain.com to OSIR"
1. Check domain info to confirm it exists
2. Ask for EPP/auth code
3. Confirm contact details
4. Initiate transfer
5. Explain the 5-7 day approval process

### "Set up a VPS in Europe"
1. List available European locations
2. Show VPS packages with specs and pricing
3. Let user select package and location
4. Confirm order details and pricing
5. Provision the VPS
6. Return connection details
```

### claw.json (ClawHub Manifest)

```json
{
  "name": "osir-registrar",
  "version": "1.0.0",
  "description": "Search, register and manage domains, VPS and dedicated servers via OSIR.COM",
  "author": "osir",
  "license": "MIT",
  "permissions": ["network"],
  "entry": "SKILL.md",
  "tags": [
    "domains", "registrar", "hosting", "vps", "dedicated-servers",
    "dns", "nameservers", "domain-registration", "web-hosting",
    "infrastructure", "devops"
  ],
  "models": ["claude-*", "gpt-*", "gemini-*"],
  "minOpenClawVersion": "0.8.0"
}
```

---

## What You Need to Build/Extend

### 1. Public MCP HTTP Endpoint

Deploy the MCP server as a Streamable HTTP endpoint (not just stdio/SSE):
- Endpoint: `POST https://mcp.osir.com/mcp`
- Protocol: JSON-RPC 2.0

### 2. Extend MCP Tools for VPS & Dedicated Servers

Current MCP server covers domains only. Add tools like:
- `listVpsLocations` / `listVpsPackages`
- `provisionVps` / `manageVps`
- `listDedicatedServers` / `orderDedicatedServer`
- `getAccountBalance` / `addFunds`

See [MCP-SERVER-IMPROVEMENT-SPEC.md](./MCP-SERVER-IMPROVEMENT-SPEC.md) for the full 10-phase expansion plan.

### 3. API Key Authentication

The skill uses `OSIR_API_KEY` as a Bearer token. Users configure in `~/.openclaw/openclaw.json`:

```json
{
  "skills": {
    "entries": {
      "osir-registrar": {
        "enabled": true,
        "apiKey": "their-osir-api-key"
      }
    }
  }
}
```

### 4. Publish to ClawHub

```bash
openclaw skill validate ./osir-registrar
openclaw auth login
openclaw skill publish ./osir-registrar
```

---

## Recommended Strategy

| Layer | Purpose | How |
|-------|---------|-----|
| **MCP Server** | Tool exposure | Deploy existing MCP as HTTP endpoint, extend with VPS/dedicated tools |
| **SKILL.md** | Agent instructions | Teaches AI how to use tools effectively, handle workflows, format responses |
| **ClawHub listing** | Discovery | Users find and install via `npx clawhub@latest install osir-registrar` |
| **REST API fallback** | Standalone use | Skill can call V2 API directly if MCP isn't configured |

The MCP approach is cleanest because OpenClaw discovers tools dynamically -- every tool added to the MCP server is instantly available without updating the skill. The SKILL.md layer adds the intelligence on *how* to use those tools well.

---

## Key References

- [OpenClaw Skills Documentation](https://docs.openclaw.ai/tools/skills)
- [ClawHub Developer Guide](https://www.digitalapplied.com/blog/clawhub-skills-marketplace-developer-guide-2026)
- [OpenClaw MCP Plugin](https://github.com/lunarpulse/openclaw-mcp-plugin)
- [OpenClaw MCP Client Feature Request](https://github.com/openclaw/openclaw/issues/8188)
- [Awesome OpenClaw Skills](https://github.com/VoltAgent/awesome-openclaw-skills)

---

## SKILL.md Format Reference

### Required Frontmatter
- `name`: Skill identifier
- `description`: Brief explanation

### Optional Frontmatter
- `homepage`: URL displayed in macOS UI
- `user-invocable`: boolean (default: `true`) - exposes as slash command
- `disable-model-invocation`: boolean (default: `false`)
- `command-dispatch`: `tool` directs slash commands directly to tools
- `metadata`: Single-line JSON for gating and configuration

### Metadata Gating Fields
- `always: true` - skip all filters
- `emoji` - macOS UI display
- `os` - platform filtering (`darwin`, `linux`, `win32`)
- `requires.bins` - required binaries on PATH
- `requires.env` - environment variables
- `requires.config` - OpenClaw config paths
- `primaryEnv` - links to `skills.entries.<name>.apiKey`

### File Organization & Precedence
1. Workspace skills (`<workspace>/skills/`) - highest priority
2. Managed/local skills (`~/.openclaw/skills/`)
3. Bundled skills - shipped with install
4. Extra directories (via `skills.load.extraDirs`) - lowest priority
