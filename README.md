# OSIR Agent Platform

AI integration servers for the [OSIR](https://osir.com) domain registrar. Connect Claude, ChatGPT,
or any AI assistant to real domain, DNS, VPS, billing, and account operations — either as
individual tools (MCP) or as task-solving specialist agents (A2A).

Two servers, one shared backend client library:

| Server | Port | Protocol | What it gives an AI |
|--------|------|----------|---------------------|
| **MCP Server** | 8081 | [Model Context Protocol](https://modelcontextprotocol.io) (SSE + Streamable HTTP) | 103 fine-grained tools (`checkDomainAvailability`, `registerDomain`, `createDnsRecord`, `createMailbox`, `osirSitePublish`, …) + 11 guided prompts |
| **A2A Server** | 8082 | [Google Agent-to-Agent](https://google.github.io/A2A/) (JSON-RPC 2.0) | 9 specialist agents with 84 skills and an orchestrator for multi-step workflows |

Use **MCP** when one assistant should call individual operations. Use **A2A** when you want to hand
a whole task ("set up example.com with DNS and check my balance") to agents that coordinate the work.

---

## What is MCP?

MCP (Model Context Protocol) is an open standard that connects AI assistants to external tools and
data. An MCP server publishes a set of callable tools; any MCP client — Claude Desktop, Cursor,
Copilot, or a custom agent — can discover and invoke them. OSIR implements an MCP server
purpose-built for domain and infrastructure management, which is what makes it an AI-native
registrar rather than a traditional one with a chat box bolted on.

## Connect OSIR to Claude

Add OSIR as a custom connector from your Claude settings — just the URL, no OAuth fields, no
config file, no install:

1. In Claude, open **Settings → Connectors**.
2. Click **Add custom connector**.
3. Set **Name** to `OSIR`.
4. Set the **Remote MCP server URL** to `https://be.osir.com/mcp/http`.
5. Save. Leave the Advanced settings (OAuth Client ID / Secret) empty.

```
Claude · Add custom connector

Name:                OSIR
Remote MCP server:   https://be.osir.com/mcp/http
```

**Signing in happens inside the conversation.** The first time your assistant needs an
authenticated tool, it starts a device login: you get a link to `auth.osir.com` and a short code,
you approve in your browser, and the assistant continues with a session scoped to that
conversation. Sessions are deliberately short-lived (they expire after ~30 minutes of inactivity,
8 hours maximum) and end instantly when you say "log me out" — so a connected chat never holds
standing access to your domains, servers, and billing.

The same URL works in any MCP client that supports a remote (streamable HTTP) server and can
drive the in-chat device login.

> **Self-hosting note:** URL-only connectors require `MCP_OAUTH_CHALLENGE_ENABLED=false` on the
> MCP server. Leaving the challenge enabled (the default) switches the server to OAuth mode
> instead: it answers unauthenticated requests with a `401` + RFC 9728 challenge and clients
> authenticate against your identity provider with a pre-registered client id. Session lifetimes
> are tunable via `MCP_SESSION_IDLE_MINUTES` and `MCP_SESSION_MAX_HOURS`.

### What your assistant can do

| Capability | Example request |
|------------|-----------------|
| Search availability | "Is coolstartup.io available, and what does it cost?" |
| Register a domain | "Register it for two years with WHOIS privacy." |
| Manage DNS | "Point it at 192.0.2.10 and add my email records." |
| Renew and transfer | "Renew everything expiring in the next 30 days." |
| Provision a VPS | "Spin up a 2 vCPU server in Frankfurt running Ubuntu." |
| Host email | "Enable email on example.com and create info@ with a 10 GB mailbox." |

---

## Running the servers yourself

Want to host your own instance or develop against the code? Requires **Java 21**; Gradle is bundled
via the wrapper.

```bash
# Run the MCP server (port 8081)
./gradlew :mcp-server:quarkusDev

# Run the A2A server (port 8082)
./gradlew :a2a-server:quarkusDev
```

Copy `.env.example` to `.env` and adjust if you're pointing at your own backend/KeyCloak/Ollama.
Everything defaults to the public OSIR endpoints, so the servers run out of the box.

### Connect an MCP client (Claude Desktop / Claude Code)

Add to your client's MCP config (e.g. `claude_desktop_config.json`):

```json
{
  "mcpServers": {
    "osir": { "url": "http://localhost:8081/mcp/sse" }
  }
}
```

Restart the client and the OSIR tools appear. Then just ask:

> "Is pizzashqip.al available? If not, suggest alternatives."
> "List all my domains and show which expire in the next 30 days."
> "Add an A record pointing example.al to 203.0.113.10 and a CNAME for www."

Most operations need authentication — ask the assistant to *"log me in to OSIR using the device
flow"* and it walks you through browser-based OAuth (KeyCloak, RFC 8628).

### Call the A2A server

```bash
# Discover the agents
curl http://localhost:8082/.well-known/agents | jq '.[].name'

# Send a task (the platform routes it to the right specialist)
curl -X POST http://localhost:8082/a2a \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "jsonrpc": "2.0", "id": "1", "method": "tasks/send",
    "params": { "message": { "role": "user",
      "parts": [{"type": "text", "text": "Check if example.com is available"}] } }
  }'
```

Tasks stream over `POST /a2a/stream` (SSE), support multi-turn `input-required` flows, and the
orchestrator decomposes complex requests across agents (max 15 steps).

---

## What you can do

**Domains** — availability, registration, renewal, transfer, lock/unlock, auto-renew, WHOIS
privacy, nameservers, AI-powered name suggestions.
**DNS** — list/create/update/delete records.
**VPS** — browse packages and locations, order, manage, panel login.
**Billing** — balance, invoices, payments, fee previews, domain pricing.
**Contacts**, **Transfers**, **Hosts**, **Audit logs**, **Account** profile & summary.

Full tool/skill catalog, example conversations, and end-to-end walkthroughs are in **[GUIDE.md](docs/GUIDE.md)**.

## Tools

105 tools, verified against the live server (tools/list on https://be.osir.com/mcp/http).

- **addPrefixToDomain** - Generate domain suggestions by adding prefixes.
- **addSshKey** - Store an SSH public key on your account so it can be injected into VPS installs.
- **addSuffixToDomain** - Generate domain suggestions by adding suffixes.
- **buildVpsInstance** - Stage an operating system install on a VPS instance.
- **bulkDomainSuggestions** - Generate domain name suggestions for one or more keywords across a chosen set of TLDs.
- **cancelTransfer** - Stage cancellation of a pending domain transfer.
- **changeVpsPaymentTerm** - Change the payment term (billing cycle) for a VPS instance.
- **checkDeviceLoginStatus** - Poll for device login completion.
- **checkDomainAvailability** - Check if a domain name is available for registration, with price.
- **checkHostAvailability** - Check if a host/glue record name is available for creation.
- **checkKeywordAvailability** - Check keyword availability across all supported TLDs and registries with detailed results.
- **checkKeywordAvailabilitySummary** - Check keyword availability summary statistics without detailed domain results (faster).
- **countMyVpsInstances** - Get the total count of VPS instances owned by the authenticated user.
- **createAccount** - Create a new OSIR customer account.
- **createContact** - Create a new contact for use with domain registrations.
- **createDnsRecord** - Create a new DNS record for a domain.
- **createHost** - Create a new host/glue record (e.g., for custom nameservers).
- **createMailbox** - Stage creation of a paid mailbox on a mail-enabled domain.
- **createPaymentSession** - Stage creation of a Stripe payment checkout session to add funds to account balance.
- **deleteContact** - Stage deletion of a contact.
- **deleteDnsRecord** - Stage deletion of a DNS record.
- **deleteHost** - Stage deletion of a host/glue record.
- **deleteMailbox** - Stage deletion of a mailbox.
- **deleteSshKey** - Remove an SSH key from your account.
- **deleteVpsInstance** - Stage deletion/cancellation of a VPS instance.
- **enableMailDomain** - Enable email hosting on a domain you own.
- **executeConfirmedAction** - Execute a previously staged destructive or financial action after user approval.
- **generateDomainSuggestions** - Generate domain name suggestions based on keywords.
- **getAccountBalance** - Get the current account balance for the authenticated user.
- **getAccountSummary** - Get a comprehensive summary of the user's account: profile, balance, domain count, VPS count, and pending transfers.
- **getAuthStatus** - Check whether the current session is authenticated.
- **getContact** - Get detailed information about a specific contact.
- **getContactsForDomain** - Get all contacts (registrant, admin, tech, billing) assigned to a domain.
- **getDedicatedServerCatalog** - Get all available dedicated server configurations with pricing and specifications.
- **getDnsRecord** - Get details of a specific DNS record.
- **getDomainAuditTrail** - Get the audit trail (history of all changes) for a specific domain.
- **getDomainExtensions** - Get all available domain extensions (TLDs) with pricing information.
- **getDomainInfo** - Get detailed information about a domain including expiration date, nameservers, and status.
- **getDomainPricing** - Get pricing for domain extensions from the product catalog.
- **getHostingBundle** - Get the hosting options and exact prices for a specific domain: recommended VPS packages (cheapest first), email plans, web forwarding, and app/site deployment (builds are free; going live runs on a VPS).
- **getHostsForDomain** - List all host/glue records associated with a domain.
- **getInvoiceDetails** - Get detailed information about a specific invoice including line items.
- **getInvoiceStatistics** - Get summary statistics of invoices: total paid, pending, overdue amounts.
- **getMailboxQuote** - Get a display-only price quote for a mailbox plan.
- **getMailboxUsage** - Get disk usage per mailbox in bytes, for quota display alongside the plan's quotaBytes.
- **getMailDnsRecords** - Get the DNS records a mail domain needs (MX, SPF, DKIM, ...) - for customers managing DNS externally.
- **getMyAuditLogs** - Get recent audit logs for the authenticated user across all services.
- **getMyProfile** - Get the authenticated user's profile and account information including name, email, organization, balance, and domain/VPS counts.
- **getPaymentTransactions** - Get payment transaction history for the authenticated user.
- **getProductCatalog** - Get the complete product catalog including domain extensions, VPS packages, and dedicated servers.
- **getRecentActivity** - Get the most recent activity across all domains and services for the user.
- **getTransferQuote** - Get a transfer price quote for a domain.
- **getTransferStatus** - Check the current status of a domain transfer.
- **getVpsInstanceDetails** - Get detailed information about a specific VPS instance including resource usage.
- **getVpsPackageDetails** - Get detailed information about a specific VPS package including all pricing tiers.
- **initializeDnsZone** - Initialize (create) the DNS zone for a domain.
- **initiateTransfer** - Stage initiation of a domain transfer from another registrar.
- **listCategorizedTlds** - List TLDs from the OSIR catalog that have category and audience metadata populated.
- **listContacts** - List all contacts for the authenticated user with optional search.
- **listDnsRecords** - List all DNS records for a domain.
- **listInvoices** - List invoices for the authenticated user with optional status filtering and pagination.
- **listMailboxes** - List your mailboxes with plan, payment term, status, and next renewal date.
- **listMailDomains** - List your domains that are enabled for email hosting, with status (PENDING_DNS or ACTIVE) and DNS mode.
- **listMailPlans** - List available email mailbox plans with quotas and prices (monthly and annual, in cents).
- **listMySshKeys** - List the SSH keys stored on your account, with their ids and SHA256 fingerprints.
- **listMyVpsInstances** - List all VPS instances owned by the authenticated user.
- **listPendingTransfers** - List all pending incoming (gaining) domain transfers.
- **listUserDomains** - List all domains owned by the authenticated user.
- **listVpsLocations** - List available VPS hosting locations (cities/countries) with available packages.
- **listVpsOsTemplates** - List operating system templates available to install.
- **listVpsPackages** - List available VPS hosting packages with pricing, specs, and locations.
- **lockDomain** - Enable registrar lock on a domain to prevent unauthorized transfers.
- **loginToVpsPanel** - Generate a one-time login URL to the VPS control panel (VirtFusion) for managing the server.
- **loginWithDevice** - Start a device authorization login (RFC 8628).
- **logout** - Log out: revokes the session's tokens at the identity provider immediately.
- **orderVps** - Stage an order for a new VPS instance.
- **osirAppCreateUpload** - Create an upload ticket for deploying app source code to Osir.
- **osirAppDelete** - Stage deletion of an Osir app.
- **osirAppDeploy** - Deploy an app to Osir (free tier) and get a live HTTPS URL; the app runs isolated in a microVM.
- **osirAppGetSource** - Get a short-lived signed download URL for an Osir app's current source zip - use this to make edits to a deployed app without the user re-attaching the project: download, patch the files, then osirAppCreateUpload (PUT the new zip) and osirAppDeploy under the SAME name; the platform rebuilds and, for owned-tier apps, auto-ships the new version to the user's box.
- **osirAppList** - List the authenticated user's deployed Osir apps with their live URLs and status.
- **osirAppLogs** - Get recent logs from an Osir app's microVM ('why is my app broken?').
- **osirAppMoveToOwned** - Move a deployed Osir app from the shared free tier onto a VPS the user owns: attach one they already have (instanceId, spends nothing) or order one (packageId, gated).
- **osirAppProvisionDatabase** - Provision a managed Postgres database for an Osir app.
- **osirAppSetSecret** - Set an environment secret for an Osir app (e.g.
- **osirAppStatus** - Get an Osir app's current status, live URL, and health ('is my app working?').
- **osirSiteDesignBrief** - Step 1 of designing a website with OSIR.
- **osirSitePublish** - Publish a single-page website to a live HTTPS URL on Osir (free tier) - ANY complete HTML document works: the user's own site, a page designed in this chat, or one from the osirSiteDesignBrief flow.
- **payInvoice** - Stage payment of an outstanding invoice from account balance.
- **previewPaymentFees** - Preview the fees that would be charged for a given payment amount.
- **registerDomain** - Stage registration of a new domain name.
- **renewDomain** - Stage renewal of a domain for a specified number of years.
- **setMailboxPassword** - Set a new password on a mailbox.
- **spinDomainWords** - Generate domain suggestions by spinning/replacing words.
- **suggestAlternatives** - Suggest alternative domain names if the requested one is unavailable (legacy method).
- **transferDomain** - Stage transfer of a domain from another registrar to OSIR.
- **unlockDomain** - Stage removal of registrar lock from a domain to allow transfers.
- **updateContact** - Update an existing contact's information.
- **updateDnsRecord** - Update an existing DNS record.
- **updateDomainAutoRenew** - Enable or disable auto-renewal for a domain.
- **updateDomainPrivacy** - Enable or disable WHOIS privacy protection for a domain.
- **updateNameservers** - Update nameservers for a domain.
- **validateDomainName** - Validate if a domain name format is correct.
- **verifyAccount** - Verify a newly created OSIR account with the code from the verification email - step 2 of onboarding, no authentication required.
- **verifyMailDns** - Check that a mail domain's DNS records resolve; activates the domain for email when all records are found.


---

## Build, test, deploy

```bash
./gradlew build        # build all modules + run tests
./gradlew test         # tests only

docker-compose up -d   # run both servers in containers
```

The provided `docker-compose.yml`, `build-and-deploy.bat`, and the CI workflow reference a
placeholder container registry (`registry.example.com`) — point them at your own.
See **[DEPLOYMENT.md](docs/DEPLOYMENT.md)** for the production checklist (PostgreSQL, TLS, scaling).

## Configuration

All settings are environment variables with sensible defaults — nothing secret is committed.

| Variable | Default | Description |
|----------|---------|-------------|
| `OSIR_BACKEND_URL` | `https://be.osir.com` | Backend API |
| `KEYCLOAK_URL` | `https://auth.osir.com` | KeyCloak auth server |
| `KEYCLOAK_REALM` | `osir` | KeyCloak realm |
| `KEYCLOAK_CLIENT_ID` | `osir-cli` | OAuth client ID |
| `OLLAMA_URL` | `http://localhost:11434` | Ollama LLM (MCP chat UI) |
| `CORS_ORIGINS` | `https://osir.com,…` | Allowed CORS origins |
| `A2A_SIGNING_SECRET` | *(empty)* | Optional HMAC-SHA256 request signing |

See [`.env.example`](.env.example) for the full list.

## Project layout

```
common/      Shared library — 12 services, 9 REST clients, ~174 models
mcp-server/  Quarkus MCP server — 105 tools, 11 prompts, 2 resources, chat UI
a2a-server/  Quarkus A2A server — 7 agents, JSON-RPC, JPA task persistence
```

Both servers depend on `common`, so a backend operation is implemented once and exposed two ways.

## Documentation

- **[GUIDE.md](docs/GUIDE.md)** — complete usage guide, tool reference, walkthroughs
- **[WEBSITE-DESIGN.md](docs/WEBSITE-DESIGN.md)** — AI website design: customer guide, use cases, frontend integration
- **[DEVELOPING.md](docs/DEVELOPING.md)** — repo layout, build/run commands, architecture notes
- **[VPS-OS-BUILD.md](docs/VPS-OS-BUILD.md)** — ordering a VPS **with an OS**, SSH keys, reinstall, and
  the OSIR APP DEPLOY flow. Read this before touching the VPS tools: ordering and building are two
  separate VirtFusion steps, and `orderVps` alone hands over a server with no operating system.
- **[A2A-API-REFERENCE.md](docs/A2A-API-REFERENCE.md)** — A2A protocol spec (methods, errors, agents)
- **[A2A-ARCHITECTURE.md](docs/A2A-ARCHITECTURE.md)** — A2A design document
- **[A2A-CONFIRMATION-GATE-SPEC.md](docs/A2A-CONFIRMATION-GATE-SPEC.md)** — staging destructive ops
  behind `executeConfirmedAction`
- **[DEPLOYMENT.md](docs/DEPLOYMENT.md)** — production deployment checklist

## License

[Apache License 2.0](LICENSE).
