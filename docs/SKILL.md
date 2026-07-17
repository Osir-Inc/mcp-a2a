# OSIR Domain Registrar — MCP Skill

## Overview

OSIR MCP Server provides 71 AI-powered tools for managing domains, DNS, VPS hosting, billing, contacts, transfers, hosts, audit logs, and account management for the OSIR domain registrar platform.

## Connection

- **Transport:** SSE
- **Endpoint:** `/mcp/sse`
- **Port:** 8081 (default)

## Authentication

Most tools require authentication. Use `loginWithDevice` for secure OAuth login or `authenticateUser` for username/password.

## Tool Categories

### Authentication (5 tools)
Login, logout, and check authentication status.

### Domain Management (12 tools)
Check availability, register, renew, lock/unlock, update nameservers, privacy, auto-renew, transfer.

### Domain Suggestions (7 tools)
AI-powered domain name generation, word spinning, prefix/suffix suggestions, keyword availability.

### DNS (5 tools)
Create, read, update, and delete DNS records (A, AAAA, CNAME, MX, TXT, SRV).

### VPS Hosting (10 tools)
Browse packages, order servers, manage instances, control panel access.

### Billing (9 tools)
Account balance, invoices, payments, pricing, transaction history.

### Contacts (6 tools)
Manage registrant and domain contact records.

### Transfers (5 tools)
Initiate, monitor, and cancel domain transfers.

### Hosts (4 tools)
Manage glue records (nameserver host entries).

### Audit (3 tools)
View audit trails, activity logs, and domain history.

### Catalog (3 tools)
Browse domain extensions, VPS packages, and dedicated server configurations.

### Account (2 tools)
User profile and account summary.

## Prompts (10 total)

- `getting_started` — Quick start guide
- `domain_registration_guide` — Domain registration best practices
- `domain_transfer_checklist` — Transfer preparation checklist
- `vps_setup_guide` — VPS provisioning walkthrough
- `dns_setup_guide` — DNS configuration guide
- `billing_overview` — Billing and payments overview
- `domain_management_guide` — Domain management best practices
- `hosting_comparison` — VPS vs dedicated comparison
- `troubleshooting` — Common issue solutions
- `security_best_practices` — Security recommendations
