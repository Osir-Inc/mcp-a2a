# OSIR MCP Server - Improvement & Extension Specification

**Version:** 2.0
**Date:** 2026-02-18
**Status:** Draft
**Author:** OSIR Engineering

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Current State Analysis](#2-current-state-analysis)
3. [Architecture & Tech Stack](#3-architecture--tech-stack)
4. [Phase 1: VPS Hosting Tools](#4-phase-1-vps-hosting-tools)
5. [Phase 2: Billing & Payment Tools](#5-phase-2-billing--payment-tools)
6. [Phase 3: DNS Management Tools](#6-phase-3-dns-management-tools)
7. [Phase 4: Domain Management Enhancements](#7-phase-4-domain-management-enhancements)
8. [Phase 5: Contact Management Tools](#8-phase-5-contact-management-tools)
9. [Phase 6: Transfer V2 Tools](#9-phase-6-transfer-v2-tools)
10. [Phase 7: Account & Profile Tools](#10-phase-7-account--profile-tools)
11. [Phase 8: Host/Glue Record Management](#11-phase-8-hostglue-record-management)
12. [Phase 9: Product Catalog Tools (Public)](#12-phase-9-product-catalog-tools-public)
13. [Phase 10: Audit & Reporting Tools](#13-phase-10-audit--reporting-tools)
14. [New MCP Prompts](#14-new-mcp-prompts)
15. [Backend Client Extensions](#15-backend-client-extensions)
16. [New Model Classes Required](#16-new-model-classes-required)
17. [Configuration Changes](#17-configuration-changes)
18. [Error Handling Standards](#18-error-handling-standards)
19. [Testing Requirements](#19-testing-requirements)
20. [OpenClaw / ClawHub Compatibility](#20-openclaw--clawhub-compatibility)
21. [Deployment & Migration](#21-deployment--migration)
22. [Appendix A: Complete Tool Reference](#appendix-a-complete-tool-reference)
23. [Appendix B: Backend API Endpoint Map](#appendix-b-backend-api-endpoint-map)

---

## 1. Executive Summary

### Purpose

This document specifies the improvements and extensions required for the OSIR MCP (Model Context Protocol) Server (`com.osir.agent`) to evolve from a domain-only tool provider into a **full-service registrar and hosting management platform** accessible via AI agents and the OpenClaw ecosystem.

### Goals

1. Extend the MCP server from **18 tools** to **~65 tools** covering all OSIR services
2. Add VPS hosting, billing, DNS, contact, and transfer management capabilities
3. Maintain backward compatibility with all existing tools
4. Enable publishing as an **OpenClaw skill** on ClawHub
5. Support both **SSE** and **Streamable HTTP** transports for broader agent compatibility

### Scope

| Area | Current Tools | Proposed Tools | Priority |
|------|:---:|:---:|:---:|
| Domain Search & Suggestions | 8 | 8 (unchanged) | - |
| Domain Registration & Management | 7 | 12 | High |
| Authentication | 3 | 3 (unchanged) | - |
| VPS Hosting | 0 | **10** | **High** |
| Billing & Payments | 0 | **9** | **High** |
| DNS Management | 0 | **5** | **Medium** |
| Contact Management | 0 | **6** | **Medium** |
| Transfer V2 | 0 | **5** | **Medium** |
| Account & Profile | 0 | **2** | **Medium** |
| Host/Glue Records | 0 | **4** | **Low** |
| Product Catalog | 0 | **3** | **Low** |
| Audit & Reporting | 0 | **3** | **Low** |
| **Total** | **18** | **~70** | |

---

## 2. Current State Analysis

### 2.1 Existing MCP Server

| Property | Value |
|----------|-------|
| **Project** | `com.osir.agent` |
| **Language** | Java 21 |
| **Framework** | Quarkus |
| **MCP SDK** | `quarkus-mcp-server-sse` v1.2.2 |
| **Transport** | Server-Sent Events (SSE) |
| **Port** | 8081 |
| **MCP Path** | `/mcp` |
| **Backend URL** | `https://be.osir.com` |

### 2.2 Existing Tools (18)

#### Authentication (3)
| Tool | Description |
|------|-------------|
| `authenticateUser` | Login with username/password |
| `getAuthStatus` | Check current authentication state |
| `logout` | Clear session |

#### Domain Availability & Suggestions (8)
| Tool | Description |
|------|-------------|
| `checkDomainAvailability` | Check single domain availability |
| `generateDomainSuggestions` | AI-powered name suggestions |
| `addPrefixToDomain` | Prefix-based suggestions |
| `addSuffixToDomain` | Suffix-based suggestions |
| `spinDomainWords` | Word replacement suggestions |
| `bulkDomainSuggestions` | Multi-keyword bulk suggestions |
| `checkKeywordAvailability` | Keyword availability across TLDs (detailed) |
| `checkKeywordAvailabilitySummary` | Keyword availability (summary, faster) |

#### Domain Operations (6)
| Tool | Description |
|------|-------------|
| `registerDomain` | Register a new domain |
| `transferDomain` | Transfer domain from another registrar |
| `updateNameservers` | Update domain nameservers |
| `getDomainInfo` | Get domain details |
| `listUserDomains` | List user's domains |
| `validateDomainName` | Validate domain format |

#### Utility (1)
| Tool | Description |
|------|-------------|
| `suggestAlternatives` | Suggest alternatives (legacy) |

### 2.3 Existing Prompts (2)
| Prompt | Description |
|--------|-------------|
| `domain_registration_guide` | Registration guidance by use case |
| `domain_transfer_checklist` | Transfer checklist |

### 2.4 Backend Client Methods Already Defined But Not Exposed

The `DomainBackendClient.java` REST client already defines methods for endpoints that have **no corresponding MCP tool**:

| Client Method | Backend Endpoint | Status |
|---------------|-----------------|--------|
| `refreshToken()` | `POST /api/auth/refresh` | Defined, not exposed |
| `bulkCheckAvailability()` | `POST /domains/bulk-availability` | Defined, not exposed |
| `getTransferStatus()` | `GET /domains/{domain}/transfer-status` | Defined, not exposed |
| `updateContact()` | `PUT /domains/{domain}/contact` | Defined, not exposed |
| `updateAutoRenew()` | `PUT /domains/{domain}/auto-renew` | Defined, not exposed |
| `updatePrivacyProtection()` | `PUT /domains/{domain}/privacy` | Defined, not exposed |
| `lockDomain()` | `POST /domains/{domain}/lock` | Defined, not exposed |
| `unlockDomain()` | `DELETE /domains/{domain}/lock` | Defined, not exposed |
| `renewDomain()` | `POST /domains/{domain}/renew` | Defined, not exposed |
| `getDomainPricing()` | `GET /pricing/domains` | Defined, not exposed |
| `getAccountBalance()` | `GET /user/billing/balance` | Defined, not exposed |

**These 11 methods should be exposed as MCP tools immediately (quick wins).**

---

## 3. Architecture & Tech Stack

### 3.1 Current Architecture

```
┌─────────────────────┐     ┌──────────────────────┐     ┌─────────────────────┐
│   AI Agent          │     │   MCP Server          │     │   Backend API       │
│   (Claude/OpenClaw) │────▶│   (com.osir.agent)    │────▶│   (domain-registrar)│
│                     │ SSE │   Port: 8081          │REST │   be.osir.com       │
│                     │◀────│   /mcp                │◀────│                     │
└─────────────────────┘     └──────────────────────┘     └─────────────────────┘
                                      │
                                      ▼
                            ┌──────────────────────┐
                            │   Ollama LLM          │
                            │   (Chat features)     │
                            └──────────────────────┘
```

### 3.2 Target Architecture

```
┌───────────────────┐
│  Claude Code      │──┐
│  (MCP Client)     │  │
└───────────────────┘  │
                       │  SSE / Streamable HTTP
┌───────────────────┐  │    ┌──────────────────────┐      ┌────────────────────┐
│  OpenClaw         │──┼───▶│   MCP Server v2.0     │─────▶│  Backend API       │
│  (AI Agent)       │  │    │   (com.osir.agent)    │ REST │  (domain-registrar)│
└───────────────────┘  │    │                       │◀─────│  be.osir.com       │
                       │    │  ~70 Tools            │      │                    │
┌───────────────────┐  │    │  10 Prompts           │      │  Endpoints:        │
│  Custom Agents    │──┘    │  2 Transports         │      │  - Domains         │
│  (MCP Clients)    │       │                       │      │  - VPS Hosting     │
└───────────────────┘       │  Port: 8081           │      │  - Billing         │
                            │  /mcp (SSE)           │      │  - DNS             │
                            │  /mcp (HTTP Stream)   │      │  - Contacts        │
                            └──────────────────────┘      │  - Transfers       │
                                                           │  - Catalog         │
                                                           └────────────────────┘
```

### 3.3 Tech Stack (No Changes)

| Component | Current | Notes |
|-----------|---------|-------|
| Java | 21 | Keep |
| Quarkus | Latest | Keep |
| MCP SDK | `quarkus-mcp-server-sse` | Upgrade if Streamable HTTP version available |
| REST Client | MicroProfile REST Client | Keep |
| Build | Gradle | Keep |

### 3.4 File Structure (Target)

```
com.osir.agent/
├── src/main/java/com/osir/mcp/
│   ├── DomainRegistrarMCPServer.java       # Existing (extend with quick-win tools)
│   ├── VpsHostingMCPServer.java            # NEW - VPS tools
│   ├── BillingMCPServer.java               # NEW - Billing & payment tools
│   ├── DnsMCPServer.java                   # NEW - DNS management tools
│   ├── ContactMCPServer.java               # NEW - Contact management tools
│   ├── TransferMCPServer.java              # NEW - V2 transfer tools
│   ├── HostMCPServer.java                  # NEW - Host/glue record tools
│   ├── CatalogMCPServer.java              # NEW - Product catalog tools
│   ├── AuditMCPServer.java                # NEW - Audit tools
│   ├── CorsFilter.java                     # Existing
│   ├── McpHealthCheck.java                 # Existing (extend)
│   ├── clients/
│   │   ├── DomainBackendClient.java        # Existing (extend)
│   │   ├── VpsBackendClient.java           # NEW
│   │   ├── BillingBackendClient.java       # NEW
│   │   ├── DnsBackendClient.java           # NEW
│   │   ├── ContactBackendClient.java       # NEW
│   │   ├── TransferBackendClient.java      # NEW
│   │   ├── HostBackendClient.java          # NEW
│   │   └── CatalogBackendClient.java       # NEW
│   ├── services/
│   │   ├── AuthService.java                # Existing
│   │   ├── SessionAwareAuthService.java    # Existing
│   │   ├── DomainService.java              # Existing
│   │   ├── VpsService.java                 # NEW
│   │   ├── BillingService.java             # NEW
│   │   ├── DnsService.java                 # NEW
│   │   └── ...
│   └── models/
│       ├── auth/                           # Existing
│       ├── contact/                        # Existing (extend)
│       ├── domain/                         # Existing
│       ├── nameserver/                     # Existing
│       ├── suggestion/                     # Existing
│       ├── vps/                            # NEW
│       ├── billing/                        # NEW
│       ├── dns/                            # NEW
│       ├── transfer/                       # NEW
│       ├── host/                           # NEW
│       └── catalog/                        # NEW
├── src/main/resources/
│   └── application.properties              # Existing (extend)
├── docs/
│   └── MCP-SERVER-IMPROVEMENT-SPEC.md      # This document
├── SKILL.md                                # NEW - OpenClaw skill definition
├── claw.json                               # NEW - ClawHub manifest
├── build.gradle                            # Existing (extend)
└── README.md                               # Existing (update)
```

---

## 4. Phase 1: VPS Hosting Tools

**Priority:** High
**Backend base path:** `/v1/hosting/vps/`
**New REST client:** `VpsBackendClient.java`
**New MCP class:** `VpsHostingMCPServer.java`

### 4.1 Tools

#### `listVpsPackages`
Browse available VPS packages with pricing and specifications.

| Field | Value |
|-------|-------|
| **Description** | List available VPS hosting packages with pricing, specs, and locations |
| **Auth Required** | No (public catalog data) |
| **Backend** | `GET /v1/public/catalog/vps` |
| **Parameters** | None |
| **Returns** | `VpsPackageListResult` |

**Result fields:**
```java
record VpsPackageListResult(
    boolean success,
    String message,
    List<VpsPackageSummary> packages
)

record VpsPackageSummary(
    String id,
    String name,
    String description,
    int cpuCores,
    int memoryMb,
    int storageGb,
    int trafficGb,
    String storageProfile,       // "SSD", "NVMe"
    long priceMonthly,           // cents
    long priceSemiAnnual,
    long priceAnnual,
    long priceBiennial,
    long priceTriennial,
    String location,             // "Nuremberg, Germany"
    String countryCode,          // "DE"
    String status                // "ACTIVE"
)
```

---

#### `listVpsLocations`
List all available VPS server locations.

| Field | Value |
|-------|-------|
| **Description** | List available VPS hosting locations (cities/countries) with available packages |
| **Auth Required** | No (public catalog data) |
| **Backend** | `GET /v1/public/catalog/vps/locations` |
| **Parameters** | None |
| **Returns** | `VpsLocationListResult` |

**Result fields:**
```java
record VpsLocationListResult(
    boolean success,
    String message,
    List<VpsLocation> locations
)

record VpsLocation(
    String id,
    String city,
    String countryName,
    String countryCode,
    String displayName,          // "Nuremberg, Germany"
    int availablePackageCount
)
```

---

#### `getVpsPackageDetails`
Get full details of a specific VPS package.

| Field | Value |
|-------|-------|
| **Description** | Get detailed information about a specific VPS package including all pricing tiers |
| **Auth Required** | No |
| **Backend** | `GET /v1/hosting/vps/packages/{packageId}` |
| **Parameters** | `packageId` (String, required) |
| **Returns** | `VpsPackageDetailResult` |

---

#### `orderVps`
Order/provision a new VPS instance.

| Field | Value |
|-------|-------|
| **Description** | Order a new VPS instance. Requires authentication. Deducts from account balance. |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/hosting/vps/order` |
| **Parameters** | |

| Parameter | Type | Required | Description |
|-----------|------|:---:|-------------|
| `packageId` | String | Yes | VPS package ID to order |
| `hostname` | String | Yes | Hostname for the VPS (e.g., 'myserver.example.com') |
| `paymentTerm` | String | Yes | Payment term: 'MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', 'TRIENNIAL' |
| `operatingSystem` | String | No | OS template to install |

**Returns:** `VpsOrderResult`

```java
record VpsOrderResult(
    boolean success,
    String message,
    String instanceId,
    String hostname,
    String packageName,
    String location,
    String status,
    String ipAddress,
    String invoiceNumber
)
```

---

#### `listMyVpsInstances`
List the authenticated user's VPS instances.

| Field | Value |
|-------|-------|
| **Description** | List all VPS instances owned by the authenticated user |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/hosting/vps/instances` |
| **Parameters** | None |
| **Returns** | `VpsInstanceListResult` |

```java
record VpsInstanceListResult(
    boolean success,
    String message,
    List<VpsInstanceSummary> instances,
    int totalCount
)

record VpsInstanceSummary(
    String id,
    String hostname,
    String packageName,
    String location,
    String status,              // "ACTIVE", "SUSPENDED", "PENDING"
    String ipAddress,
    int cpuCores,
    int memoryMb,
    int storageGb,
    String paymentTerm,
    String nextRenewalDate,
    String createdAt
)
```

---

#### `getVpsInstanceDetails`
Get full details of a specific VPS instance.

| Field | Value |
|-------|-------|
| **Description** | Get detailed information about a specific VPS instance including resource usage |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/hosting/vps/instances/{instanceId}` |
| **Parameters** | `instanceId` (String, required) |
| **Returns** | `VpsInstanceDetailResult` |

---

#### `deleteVpsInstance`
Request deletion of a VPS instance.

| Field | Value |
|-------|-------|
| **Description** | Request deletion/cancellation of a VPS instance. This action is irreversible. |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/hosting/vps/instances/{instanceId}/delete` |
| **Parameters** | `instanceId` (String, required) |
| **Returns** | `VpsActionResult` |

---

#### `changeVpsPaymentTerm`
Change the billing cycle for a VPS instance.

| Field | Value |
|-------|-------|
| **Description** | Change the payment term (billing cycle) for a VPS instance |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/hosting/vps/instances/{instanceId}/change-payment-term` |
| **Parameters** | `instanceId` (String, required), `paymentTerm` (String, required: 'MONTHLY', 'SEMI_ANNUAL', 'ANNUAL', 'BIENNIAL', 'TRIENNIAL') |
| **Returns** | `VpsActionResult` |

---

#### `loginToVpsPanel`
Get a login URL for the VPS control panel (VirtFusion).

| Field | Value |
|-------|-------|
| **Description** | Generate a one-time login URL to the VPS control panel for managing the server |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/hosting/vps/instances/{instanceId}/login` |
| **Parameters** | `instanceId` (String, required) |
| **Returns** | `VpsPanelLoginResult` |

```java
record VpsPanelLoginResult(
    boolean success,
    String message,
    String loginUrl
)
```

---

#### `countMyVpsInstances`
Get count of user's VPS instances.

| Field | Value |
|-------|-------|
| **Description** | Get the total count of VPS instances owned by the authenticated user |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/hosting/vps/instances/count` |
| **Parameters** | None |
| **Returns** | `VpsCountResult` |

---

## 5. Phase 2: Billing & Payment Tools

**Priority:** High
**Backend base paths:** `/v1/billing/invoices/`, `/v1/payment/`
**New REST client:** `BillingBackendClient.java`
**New MCP class:** `BillingMCPServer.java`

### 5.1 Tools

#### `getAccountBalance`
Get the user's current account balance.

| Field | Value |
|-------|-------|
| **Description** | Get the current account balance for the authenticated user |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/payment/balance` |
| **Parameters** | None |
| **Returns** | `AccountBalanceResult` |

```java
record AccountBalanceResult(
    boolean success,
    String message,
    String balance,             // formatted, e.g. "125.50"
    String currency             // "USD"
)
```

> **Note:** `DomainBackendClient` already defines `getAccountBalance()` calling `GET /user/billing/balance`. Verify which endpoint is canonical and use that.

---

#### `listInvoices`
List the user's invoices with filtering.

| Field | Value |
|-------|-------|
| **Description** | List invoices for the authenticated user with optional status filtering and pagination |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/billing/invoices` |
| **Parameters** | |

| Parameter | Type | Required | Description |
|-----------|------|:---:|-------------|
| `status` | String | No | Filter by status: 'DRAFT', 'PENDING', 'PAID', 'CANCELLED', 'OVERDUE' |
| `page` | Integer | No | Page number (default 0) |
| `size` | Integer | No | Page size (default 20) |

**Returns:** `InvoiceListResult`

```java
record InvoiceListResult(
    boolean success,
    String message,
    List<InvoiceSummary> invoices,
    int totalCount,
    int totalPages
)

record InvoiceSummary(
    String id,
    String invoiceNumber,
    String status,
    String totalAmount,
    String currency,
    String invoiceDate,
    String dueDate,
    String paidDate,
    String type                  // "DOMAIN", "VPS", "GENERAL"
)
```

---

#### `getInvoiceDetails`
Get full details of a specific invoice.

| Field | Value |
|-------|-------|
| **Description** | Get detailed information about a specific invoice including line items |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/billing/invoices/{invoiceId}` |
| **Parameters** | `invoiceId` (String, required) |
| **Returns** | `InvoiceDetailResult` |

```java
record InvoiceDetailResult(
    boolean success,
    String message,
    String id,
    String invoiceNumber,
    String status,
    String totalAmount,
    String currency,
    String invoiceDate,
    String dueDate,
    String paidDate,
    List<InvoiceItemDetail> items
)

record InvoiceItemDetail(
    String id,
    String description,
    String details,
    String unitPrice,
    int quantity,
    String totalPrice
)
```

---

#### `payInvoice`
Pay an invoice from account balance.

| Field | Value |
|-------|-------|
| **Description** | Pay an outstanding invoice using the account balance. Requires sufficient balance. |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/billing/invoices/{invoiceId}/pay` |
| **Parameters** | `invoiceId` (String, required) |
| **Returns** | `PaymentResult` |

```java
record PaymentResult(
    boolean success,
    String message,
    String invoiceNumber,
    String amountPaid,
    String remainingBalance
)
```

---

#### `getInvoiceStatistics`
Get invoice statistics/summary for the user.

| Field | Value |
|-------|-------|
| **Description** | Get summary statistics of invoices: total paid, pending, overdue amounts |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/billing/invoices/statistics` |
| **Parameters** | None |
| **Returns** | `InvoiceStatisticsResult` |

---

#### `createPaymentSession`
Create a payment checkout session (e.g., Stripe) to add funds.

| Field | Value |
|-------|-------|
| **Description** | Create a payment checkout session to add funds to account balance via Stripe |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/payment/checkout-session` |
| **Parameters** | `amount` (Double, required, in USD), `currency` (String, optional, default 'USD') |
| **Returns** | `PaymentSessionResult` |

```java
record PaymentSessionResult(
    boolean success,
    String message,
    String sessionId,
    String checkoutUrl          // URL to redirect user to for payment
)
```

---

#### `getPaymentTransactions`
List payment transactions.

| Field | Value |
|-------|-------|
| **Description** | Get payment transaction history for the authenticated user |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/payment/transactions` |
| **Parameters** | `page` (Integer, optional), `size` (Integer, optional) |
| **Returns** | `TransactionListResult` |

---

#### `previewPaymentFees`
Preview fees before making a payment.

| Field | Value |
|-------|-------|
| **Description** | Preview the fees that would be charged for a given payment amount |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/payment/fee-preview` |
| **Parameters** | `amount` (Double, required), `currency` (String, optional) |
| **Returns** | `FeePreviewResult` |

---

#### `getDomainPricing`
Get pricing for domain operations.

| Field | Value |
|-------|-------|
| **Description** | Get pricing for domain registration, renewal, or transfer for a specific TLD |
| **Auth Required** | **Yes** |
| **Backend** | `GET /pricing/domains` |
| **Parameters** | `tld` (String, optional, e.g. 'com'), `operation` (String, optional: 'register', 'renew', 'transfer') |
| **Returns** | `DomainPricingResult` |

```java
record DomainPricingResult(
    boolean success,
    String message,
    List<PricingEntry> pricing
)

record PricingEntry(
    String tld,
    String operation,
    String price1Year,
    String price2Year,
    String price3Year,
    String currency
)
```

---

## 6. Phase 3: DNS Management Tools

**Priority:** Medium
**Backend base path:** `/dns/`
**New REST client:** `DnsBackendClient.java`
**New MCP class:** `DnsMCPServer.java`

### 6.1 Tools

#### `listDnsRecords`

| Field | Value |
|-------|-------|
| **Description** | List all DNS records for a domain |
| **Auth Required** | **Yes** |
| **Backend** | `GET /dns/domains/{domain}/records` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `DnsRecordListResult` |

```java
record DnsRecordListResult(
    boolean success,
    String message,
    String domain,
    List<DnsRecord> records
)

record DnsRecord(
    String id,
    String name,               // e.g., "www", "@", "mail"
    String type,               // "A", "AAAA", "CNAME", "MX", "TXT", "NS", "SRV"
    String content,            // "93.184.216.34"
    int ttl,                   // 3600
    int priority,              // MX priority (0 for non-MX)
    boolean disabled
)
```

---

#### `createDnsRecord`

| Field | Value |
|-------|-------|
| **Description** | Create a new DNS record for a domain |
| **Auth Required** | **Yes** |
| **Backend** | `POST /dns/domains/{domain}/records` |
| **Parameters** | |

| Parameter | Type | Required | Description |
|-----------|------|:---:|-------------|
| `domain` | String | Yes | Domain name (e.g., 'example.com') |
| `name` | String | Yes | Record name (e.g., 'www', '@', 'mail') |
| `type` | String | Yes | Record type: 'A', 'AAAA', 'CNAME', 'MX', 'TXT', 'NS', 'SRV' |
| `content` | String | Yes | Record content/value |
| `ttl` | Integer | No | Time to live in seconds (default 3600) |
| `priority` | Integer | No | Priority for MX/SRV records (default 0) |

**Returns:** `DnsRecordResult`

---

#### `updateDnsRecord`

| Field | Value |
|-------|-------|
| **Description** | Update an existing DNS record |
| **Auth Required** | **Yes** |
| **Backend** | `PUT /dns/domains/{domain}/records/{recordId}` |
| **Parameters** | `domain` (String, req), `recordId` (String, req), `name` (String, opt), `type` (String, opt), `content` (String, opt), `ttl` (Integer, opt), `priority` (Integer, opt) |
| **Returns** | `DnsRecordResult` |

---

#### `deleteDnsRecord`

| Field | Value |
|-------|-------|
| **Description** | Delete a DNS record |
| **Auth Required** | **Yes** |
| **Backend** | `DELETE /dns/domains/{domain}/records/{recordId}` |
| **Parameters** | `domain` (String, required), `recordId` (String, required) |
| **Returns** | `DnsActionResult` |

---

#### `getDnsRecord`

| Field | Value |
|-------|-------|
| **Description** | Get details of a specific DNS record |
| **Auth Required** | **Yes** |
| **Backend** | `GET /dns/domains/{domain}/records/{recordId}` |
| **Parameters** | `domain` (String, required), `recordId` (String, required) |
| **Returns** | `DnsRecordResult` |

---

## 7. Phase 4: Domain Management Enhancements

**Priority:** High (quick wins - backend client methods already exist)
**Extends:** `DomainRegistrarMCPServer.java` and `DomainBackendClient.java`

These tools expose backend client methods that are **already defined** but have no MCP tool.

### 7.1 Tools

#### `renewDomain`

| Field | Value |
|-------|-------|
| **Description** | Renew a domain for a specified number of years. Deducts from account balance. |
| **Auth Required** | **Yes** |
| **Backend** | `POST /domains/{domain}/renew` |
| **Parameters** | `domain` (String, required), `years` (Integer, required, 1-10) |
| **Returns** | `DomainRenewalResult` |

```java
record DomainRenewalResult(
    boolean success,
    String message,
    String domain,
    String newExpirationDate,
    String renewalCost,
    String invoiceNumber
)
```

---

#### `lockDomain`

| Field | Value |
|-------|-------|
| **Description** | Enable registrar lock on a domain to prevent unauthorized transfers |
| **Auth Required** | **Yes** |
| **Backend** | `POST /domains/{domain}/lock` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `DomainActionResult` |

---

#### `unlockDomain`

| Field | Value |
|-------|-------|
| **Description** | Remove registrar lock from a domain to allow transfers |
| **Auth Required** | **Yes** |
| **Backend** | `DELETE /domains/{domain}/lock` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `DomainActionResult` |

---

#### `updateDomainAutoRenew`

| Field | Value |
|-------|-------|
| **Description** | Enable or disable auto-renewal for a domain |
| **Auth Required** | **Yes** |
| **Backend** | `PUT /domains/{domain}/auto-renew` |
| **Parameters** | `domain` (String, required), `enabled` (Boolean, required) |
| **Returns** | `DomainActionResult` |

---

#### `updateDomainPrivacy`

| Field | Value |
|-------|-------|
| **Description** | Enable or disable WHOIS privacy protection for a domain |
| **Auth Required** | **Yes** |
| **Backend** | `PUT /domains/{domain}/privacy` |
| **Parameters** | `domain` (String, required), `enabled` (Boolean, required) |
| **Returns** | `DomainActionResult` |

---

## 8. Phase 5: Contact Management Tools

**Priority:** Medium
**Backend base path:** `/v1/contacts/`
**New REST client:** `ContactBackendClient.java`
**New MCP class:** `ContactMCPServer.java`

### 8.1 Tools

#### `listContacts`

| Field | Value |
|-------|-------|
| **Description** | List all contacts for the authenticated user with optional search |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/contacts` |
| **Parameters** | `search` (String, optional - search by name/email/org) |
| **Returns** | `ContactListResult` |

---

#### `getContact`

| Field | Value |
|-------|-------|
| **Description** | Get detailed information about a specific contact |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/contacts/{id}` |
| **Parameters** | `contactId` (String, required) |
| **Returns** | `ContactDetailResult` |

---

#### `createContact`

| Field | Value |
|-------|-------|
| **Description** | Create a new contact for use with domain registrations |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v1/contacts` |
| **Parameters** | |

| Parameter | Type | Required | Description |
|-----------|------|:---:|-------------|
| `firstName` | String | Yes | First name |
| `lastName` | String | Yes | Last name |
| `email` | String | Yes | Email address |
| `phone` | String | Yes | Phone number (E.164 format, e.g. '+1.5551234567') |
| `organization` | String | No | Organization/company name |
| `street1` | String | Yes | Street address line 1 |
| `street2` | String | No | Street address line 2 |
| `city` | String | Yes | City |
| `state` | String | No | State/province |
| `postalCode` | String | Yes | Postal/ZIP code |
| `country` | String | Yes | Country code (ISO 3166-1 alpha-2, e.g., 'US', 'DE') |

**Returns:** `ContactResult`

---

#### `updateContact`

| Field | Value |
|-------|-------|
| **Description** | Update an existing contact's information |
| **Auth Required** | **Yes** |
| **Backend** | `PUT /v1/contacts/{id}` |
| **Parameters** | `contactId` (String, required) + same fields as `createContact` (all optional for update) |
| **Returns** | `ContactResult` |

---

#### `deleteContact`

| Field | Value |
|-------|-------|
| **Description** | Delete a contact. Cannot delete if assigned to active domains. |
| **Auth Required** | **Yes** |
| **Backend** | `DELETE /v1/contacts/{id}` |
| **Parameters** | `contactId` (String, required) |
| **Returns** | `ContactActionResult` |

---

#### `getContactsForDomain`

| Field | Value |
|-------|-------|
| **Description** | Get all contacts (registrant, admin, tech, billing) assigned to a domain |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/contacts/for-domain/{domainId}/all` |
| **Parameters** | `domainId` (String, required) |
| **Returns** | `DomainContactsResult` |

---

## 9. Phase 6: Transfer V2 Tools

**Priority:** Medium
**Backend base path:** `/v2/transfer/`
**New REST client:** `TransferBackendClient.java`
**New MCP class:** `TransferMCPServer.java`

### 9.1 Tools

#### `getTransferQuote`

| Field | Value |
|-------|-------|
| **Description** | Get a price quote for transferring a domain to OSIR |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v2/transfer/{domain}/quote` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `TransferQuoteResult` |

```java
record TransferQuoteResult(
    boolean success,
    String message,
    String domain,
    String transferPrice,
    String currency,
    String extensionYears,       // typically "1"
    String newExpirationDate
)
```

---

#### `initiateTransfer`

| Field | Value |
|-------|-------|
| **Description** | Initiate a domain transfer to OSIR. Requires the EPP/auth code from the current registrar. |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v2/transfer/initiate` |
| **Parameters** | `domain` (String, required), `authCode` (String, required - EPP/auth code from current registrar) |
| **Returns** | `TransferInitiateResult` |

---

#### `getTransferStatus`

| Field | Value |
|-------|-------|
| **Description** | Check the current status of a domain transfer |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v2/transfer/{domain}/status` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `TransferStatusResult` |

---

#### `cancelTransfer`

| Field | Value |
|-------|-------|
| **Description** | Cancel a pending domain transfer |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v2/transfer/{domain}/cancel` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `TransferActionResult` |

---

#### `listPendingTransfers`

| Field | Value |
|-------|-------|
| **Description** | List all pending incoming domain transfers |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v2/transfer/gaining/pending` |
| **Parameters** | None |
| **Returns** | `PendingTransferListResult` |

```java
record PendingTransferListResult(
    boolean success,
    String message,
    List<PendingTransfer> transfers
)

record PendingTransfer(
    String domain,
    String status,              // "PENDING", "APPROVED", "REJECTED"
    String requestDate,
    String currentRegistrar,
    String expectedCompletion
)
```

---

## 10. Phase 7: Account & Profile Tools

**Priority:** Medium
**Backend base path:** `/v1/customers/`
**Extends:** `DomainBackendClient.java`

### 10.1 Tools

#### `getMyProfile`

| Field | Value |
|-------|-------|
| **Description** | Get the authenticated user's profile and account information |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/customers/me` |
| **Parameters** | None |
| **Returns** | `UserProfileResult` |

```java
record UserProfileResult(
    boolean success,
    String message,
    String customerId,
    String name,
    String email,
    String organization,
    String balance,
    String currency,
    int domainCount,
    int vpsCount
)
```

---

#### `getAccountSummary`

| Field | Value |
|-------|-------|
| **Description** | Get a comprehensive summary of the user's account: domains, VPS, balance, pending actions |
| **Auth Required** | **Yes** |
| **Backend** | Multiple calls aggregated |
| **Parameters** | None |
| **Returns** | `AccountSummaryResult` |

> **Implementation note:** This is a **composite tool** that calls multiple backend endpoints and aggregates results. It should call: `GET /v1/customers/me`, `GET /v1/payment/balance`, `GET /v1/hosting/vps/instances/count`, `GET /v2/transfer/gaining/pending`.

---

## 11. Phase 8: Host/Glue Record Management

**Priority:** Low
**Backend base path:** `/v2/hosts/`
**New REST client:** `HostBackendClient.java`
**New MCP class:** `HostMCPServer.java`

### 11.1 Tools

#### `checkHostAvailability`

| Field | Value |
|-------|-------|
| **Description** | Check if a hostname is available for creation as a glue record |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v2/hosts/check` |
| **Parameters** | `hostname` (String, required, e.g. 'ns1.example.com') |
| **Returns** | `HostCheckResult` |

---

#### `createHost`

| Field | Value |
|-------|-------|
| **Description** | Create a host/glue record (nameserver registration under your own domain) |
| **Auth Required** | **Yes** |
| **Backend** | `POST /v2/hosts` |
| **Parameters** | `hostname` (String, required), `ipAddresses` (List<String>, required - IPv4/IPv6 addresses) |
| **Returns** | `HostResult` |

---

#### `getHostsForDomain`

| Field | Value |
|-------|-------|
| **Description** | List all host/glue records registered under a domain |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v2/hosts/domain/{domain}` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `HostListResult` |

---

#### `deleteHost`

| Field | Value |
|-------|-------|
| **Description** | Delete a host/glue record |
| **Auth Required** | **Yes** |
| **Backend** | `DELETE /v2/hosts/{hostname}` |
| **Parameters** | `hostname` (String, required) |
| **Returns** | `HostActionResult` |

---

## 12. Phase 9: Product Catalog Tools (Public)

**Priority:** Low
**Backend base path:** `/v1/public/catalog/`
**New REST client:** `CatalogBackendClient.java`
**New MCP class:** `CatalogMCPServer.java`

### 12.1 Tools

#### `getProductCatalog`

| Field | Value |
|-------|-------|
| **Description** | Get the complete product catalog including domains, VPS, and dedicated servers |
| **Auth Required** | No (public) |
| **Backend** | `GET /v1/public/catalog` |
| **Parameters** | None |
| **Returns** | `ProductCatalogResult` |

---

#### `getDomainExtensions`

| Field | Value |
|-------|-------|
| **Description** | Get all available domain extensions (TLDs) with registration and renewal pricing |
| **Auth Required** | No (public) |
| **Backend** | `GET /v1/public/catalog/domains` |
| **Parameters** | None |
| **Returns** | `DomainExtensionsResult` |

```java
record DomainExtensionsResult(
    boolean success,
    String message,
    List<DomainExtension> extensions
)

record DomainExtension(
    String tld,                 // "com", "net", "org"
    String registerPrice,
    String renewPrice,
    String transferPrice,
    String currency,
    boolean available,
    String registry             // "Verisign", "PIR", etc.
)
```

---

#### `getDedicatedServerCatalog`

| Field | Value |
|-------|-------|
| **Description** | Get available dedicated server configurations with pricing |
| **Auth Required** | No (public) |
| **Backend** | `GET /v1/public/catalog/dedicated` |
| **Parameters** | None |
| **Returns** | `DedicatedServerCatalogResult` |

---

## 13. Phase 10: Audit & Reporting Tools

**Priority:** Low
**Backend base path:** `/v1/audit/`
**Extends:** `DomainBackendClient.java`

### 13.1 Tools

#### `getDomainAuditTrail`

| Field | Value |
|-------|-------|
| **Description** | Get the audit trail (history of all changes) for a specific domain |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/audit/domain/{domain}` |
| **Parameters** | `domain` (String, required) |
| **Returns** | `AuditTrailResult` |

```java
record AuditTrailResult(
    boolean success,
    String message,
    String domain,
    List<AuditEntry> entries
)

record AuditEntry(
    String action,              // "DOMAIN_CREATE", "NAMESERVER_UPDATE", etc.
    String actor,
    String actorType,           // "CUSTOMER", "ADMIN", "SYSTEM"
    String timestamp,
    String details,
    boolean wasSuccessful
)
```

---

#### `getMyAuditLogs`

| Field | Value |
|-------|-------|
| **Description** | Get recent audit logs for the authenticated user across all services |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/audit/customer/{customerId}` |
| **Parameters** | `page` (Integer, optional), `size` (Integer, optional) |
| **Returns** | `AuditLogListResult` |

---

#### `getRecentActivity`

| Field | Value |
|-------|-------|
| **Description** | Get the most recent activity across all domains and services for the user |
| **Auth Required** | **Yes** |
| **Backend** | `GET /v1/audit/recent` |
| **Parameters** | None |
| **Returns** | `RecentActivityResult` |

---

## 14. New MCP Prompts

### 14.1 Existing Prompts (Keep)
- `domain_registration_guide` - Domain registration guidance
- `domain_transfer_checklist` - Transfer checklist

### 14.2 New Prompts

#### `vps_setup_guide`

| Field | Value |
|-------|-------|
| **Description** | Guide for choosing and setting up a VPS based on use case |
| **Parameters** | `use_case` (String: 'web_hosting', 'game_server', 'development', 'database', 'general') |

**Content should cover:**
- Recommended VPS size for the use case
- Location selection guidance
- OS recommendation
- Post-setup steps (firewall, SSH, updates)
- Common software to install

---

#### `dns_setup_guide`

| Field | Value |
|-------|-------|
| **Description** | Guide for setting up DNS records for common scenarios |
| **Parameters** | `scenario` (String: 'website', 'email', 'subdomain', 'redirect', 'full_setup') |

**Content should cover:**
- Required A/AAAA records
- MX records for email
- TXT records (SPF, DKIM, DMARC)
- CNAME for subdomains
- Common mistakes to avoid

---

#### `billing_overview`

| Field | Value |
|-------|-------|
| **Description** | Overview of billing, payment methods, and invoice management |
| **Parameters** | None |

---

#### `domain_management_guide`

| Field | Value |
|-------|-------|
| **Description** | Guide for managing domains: locking, privacy, auto-renew, contacts |
| **Parameters** | `topic` (String: 'security', 'renewal', 'privacy', 'contacts', 'general') |

---

#### `hosting_comparison`

| Field | Value |
|-------|-------|
| **Description** | Compare VPS packages or VPS vs dedicated server options |
| **Parameters** | `comparison_type` (String: 'vps_packages', 'vps_vs_dedicated', 'locations') |

---

#### `getting_started`

| Field | Value |
|-------|-------|
| **Description** | Getting started guide for new users of the OSIR platform |
| **Parameters** | None |

**Content should cover:**
- Account setup
- Adding funds
- Registering first domain
- Setting up DNS
- Ordering VPS
- Managing services

---

#### `troubleshooting`

| Field | Value |
|-------|-------|
| **Description** | Common troubleshooting steps for domain, DNS, VPS, and billing issues |
| **Parameters** | `category` (String: 'domain', 'dns', 'vps', 'billing', 'transfer') |

---

#### `security_best_practices`

| Field | Value |
|-------|-------|
| **Description** | Security best practices for domain and hosting management |
| **Parameters** | None |

---

## 15. Backend Client Extensions

### 15.1 New REST Client Interfaces

Each new client follows the same pattern as `DomainBackendClient.java`:

```java
@RegisterRestClient(configKey = "domain-backend")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface VpsBackendClient {
    // Methods with @GET, @POST, @PUT, @DELETE
    // Bearer token passed via @HeaderParam("Authorization")
}
```

> **Important:** All new clients should use the same `configKey = "domain-backend"` to share the backend URL configuration. They connect to the **same backend API** at `be.osir.com`.

### 15.2 Client-to-Backend Endpoint Map

| Client | Endpoint Count | Backend Paths |
|--------|:-:|---|
| `VpsBackendClient` | 10 | `/v1/hosting/vps/*`, `/v1/public/catalog/vps/*` |
| `BillingBackendClient` | 9 | `/v1/billing/invoices/*`, `/v1/payment/*`, `/pricing/*` |
| `DnsBackendClient` | 5 | `/dns/domains/*` |
| `ContactBackendClient` | 6 | `/v1/contacts/*` |
| `TransferBackendClient` | 5 | `/v2/transfer/*` |
| `HostBackendClient` | 4 | `/v2/hosts/*` |
| `CatalogBackendClient` | 3 | `/v1/public/catalog/*` |
| `DomainBackendClient` (extended) | +5 | Existing + audit endpoints |

---

## 16. New Model Classes Required

### 16.1 VPS Models (`models/vps/`)

| Class | Type | Purpose |
|-------|------|---------|
| `VpsPackageSummary` | Record | VPS package overview |
| `VpsPackageDetail` | Record | Full VPS package info |
| `VpsLocation` | Record | VPS location info |
| `VpsOrderRequest` | Record | VPS order request body |
| `VpsOrderResponse` | Record | Backend order response |
| `VpsInstanceSummary` | Record | VPS instance overview |
| `VpsInstanceDetail` | Record | Full VPS instance info |
| `VpsActionResponse` | Record | Generic VPS action response |
| `VpsPanelLoginResponse` | Record | Panel login URL response |
| **Result types** | Records | `VpsPackageListResult`, `VpsLocationListResult`, `VpsOrderResult`, `VpsInstanceListResult`, `VpsInstanceDetailResult`, `VpsActionResult`, `VpsPanelLoginResult`, `VpsCountResult` |

### 16.2 Billing Models (`models/billing/`)

| Class | Type | Purpose |
|-------|------|---------|
| `InvoiceSummary` | Record | Invoice overview |
| `InvoiceDetail` | Record | Full invoice with items |
| `InvoiceItemDetail` | Record | Single invoice line item |
| `PaymentSessionRequest` | Record | Payment session request body |
| `PaymentSessionResponse` | Record | Checkout URL response |
| `TransactionSummary` | Record | Payment transaction |
| `FeePreview` | Record | Fee calculation |
| `PricingEntry` | Record | Domain pricing per TLD |
| **Result types** | Records | `AccountBalanceResult`, `InvoiceListResult`, `InvoiceDetailResult`, `PaymentResult`, `InvoiceStatisticsResult`, `PaymentSessionResult`, `TransactionListResult`, `FeePreviewResult`, `DomainPricingResult` |

### 16.3 DNS Models (`models/dns/`)

| Class | Type | Purpose |
|-------|------|---------|
| `DnsRecord` | Record | DNS record data |
| `DnsRecordRequest` | Record | Create/update request body |
| **Result types** | Records | `DnsRecordListResult`, `DnsRecordResult`, `DnsActionResult` |

### 16.4 Transfer Models (`models/transfer/`)

| Class | Type | Purpose |
|-------|------|---------|
| `TransferQuote` | Record | Transfer pricing quote |
| `TransferInitiateRequest` | Record | Transfer initiation request body |
| `PendingTransfer` | Record | Pending transfer summary |
| **Result types** | Records | `TransferQuoteResult`, `TransferInitiateResult`, `TransferStatusResult`, `TransferActionResult`, `PendingTransferListResult` |

### 16.5 Host Models (`models/host/`)

| Class | Type | Purpose |
|-------|------|---------|
| `HostRecord` | Record | Host/glue record data |
| `HostCreateRequest` | Record | Host creation request body |
| **Result types** | Records | `HostCheckResult`, `HostResult`, `HostListResult`, `HostActionResult` |

### 16.6 Catalog Models (`models/catalog/`)

| Class | Type | Purpose |
|-------|------|---------|
| `DomainExtension` | Record | TLD with pricing |
| `DedicatedServerConfig` | Record | Dedicated server offering |
| **Result types** | Records | `ProductCatalogResult`, `DomainExtensionsResult`, `DedicatedServerCatalogResult` |

### 16.7 Audit Models (`models/audit/`)

| Class | Type | Purpose |
|-------|------|---------|
| `AuditEntry` | Record | Single audit log entry |
| **Result types** | Records | `AuditTrailResult`, `AuditLogListResult`, `RecentActivityResult` |

### 16.8 Domain Enhancement Models (extend `models/domain/`)

| Class | Type | Purpose |
|-------|------|---------|
| `DomainRenewalRequest` | Record | Renewal request body |
| `AutoRenewRequest` | Record | Auto-renew toggle request |
| `PrivacyRequest` | Record | Privacy toggle request |
| `DomainLockResponse` | Record | Lock/unlock response |
| **Result types** | Records | `DomainRenewalResult`, `DomainActionResult` |

### 16.9 Account Models (extend `models/auth/`)

| Class | Type | Purpose |
|-------|------|---------|
| **Result types** | Records | `UserProfileResult`, `AccountSummaryResult` |

---

## 17. Configuration Changes

### 17.1 application.properties Additions

```properties
# ============================================================
# MCP Server v2.0 Configuration
# ============================================================

# Transport (add Streamable HTTP when SDK supports it)
quarkus.mcp.server.sse.root-path=mcp

# Backend API (shared across all clients)
quarkus.rest-client."domain-backend".url=${BACKEND_SERVICE_URL:https://be.osir.com}
quarkus.rest-client."domain-backend".scope=jakarta.inject.Singleton
quarkus.rest-client."domain-backend".connect-timeout=5000
quarkus.rest-client."domain-backend".read-timeout=30000

# Feature flags for progressive rollout
mcp.features.vps.enabled=${MCP_VPS_ENABLED:true}
mcp.features.billing.enabled=${MCP_BILLING_ENABLED:true}
mcp.features.dns.enabled=${MCP_DNS_ENABLED:true}
mcp.features.contacts.enabled=${MCP_CONTACTS_ENABLED:true}
mcp.features.transfer-v2.enabled=${MCP_TRANSFER_V2_ENABLED:true}
mcp.features.hosts.enabled=${MCP_HOSTS_ENABLED:true}
mcp.features.catalog.enabled=${MCP_CATALOG_ENABLED:true}
mcp.features.audit.enabled=${MCP_AUDIT_ENABLED:true}

# Logging
quarkus.log.category."com.osir.mcp".level=DEBUG
quarkus.log.category."com.osir.mcp.clients".level=INFO
```

### 17.2 Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_SERVICE_URL` | `https://be.osir.com` | Backend API base URL |
| `MCP_VPS_ENABLED` | `true` | Enable VPS hosting tools |
| `MCP_BILLING_ENABLED` | `true` | Enable billing tools |
| `MCP_DNS_ENABLED` | `true` | Enable DNS tools |
| `MCP_CONTACTS_ENABLED` | `true` | Enable contact tools |
| `MCP_TRANSFER_V2_ENABLED` | `true` | Enable V2 transfer tools |
| `MCP_HOSTS_ENABLED` | `true` | Enable host/glue record tools |
| `MCP_CATALOG_ENABLED` | `true` | Enable catalog tools |
| `MCP_AUDIT_ENABLED` | `true` | Enable audit tools |

---

## 18. Error Handling Standards

### 18.1 Result Pattern (Existing - Maintain)

All tools MUST return result objects, never throw exceptions to the MCP client.

```java
// Standard result pattern
record SomeResult(
    boolean success,
    String message,
    // ... operation-specific fields
) {}
```

### 18.2 Error Categories

| Category | Message Prefix | Example |
|----------|---------------|---------|
| Authentication required | `"Authentication required. ..."` | User not logged in |
| Not found | `"Not found: ..."` | Domain/invoice/instance doesn't exist |
| Validation error | `"Validation error: ..."` | Invalid domain format, missing field |
| Insufficient balance | `"Insufficient balance: ..."` | Can't afford operation |
| Permission denied | `"Permission denied: ..."` | User doesn't own resource |
| Backend error | `"Service error: ..."` | Backend API returned error |
| Network error | `"Connection error: ..."` | Backend unreachable |

### 18.3 Standard Error Handling Template

```java
@Tool(description = "...")
public SomeResult someOperation(String param) {
    // 1. Auth check
    if (!authService.isAuthenticated()) {
        return new SomeResult(false,
            "Authentication required. Please login first using authenticateUser.");
    }

    // 2. Input validation
    if (param == null || param.isBlank()) {
        return new SomeResult(false,
            "Validation error: parameter is required.");
    }

    // 3. Execute
    try {
        String token = "Bearer " + authService.getToken();
        var response = backendClient.someEndpoint(param, token);
        return new SomeResult(true, "Success", /* fields */);
    } catch (WebApplicationException e) {
        int status = e.getResponse().getStatus();
        if (status == 404) {
            return new SomeResult(false, "Not found: resource does not exist.");
        } else if (status == 403) {
            return new SomeResult(false, "Permission denied: you do not have access.");
        } else if (status == 402) {
            return new SomeResult(false, "Insufficient balance for this operation.");
        }
        return new SomeResult(false, "Service error: " + e.getMessage());
    } catch (Exception e) {
        LOG.errorf(e, "Error in someOperation: %s", e.getMessage());
        return new SomeResult(false, "Connection error: " + e.getMessage());
    }
}
```

---

## 19. Testing Requirements

### 19.1 Unit Tests

Each MCP tool class should have corresponding unit tests:

| Test Class | Covers |
|------------|--------|
| `VpsHostingMCPServerTest` | All 10 VPS tools |
| `BillingMCPServerTest` | All 9 billing tools |
| `DnsMCPServerTest` | All 5 DNS tools |
| `ContactMCPServerTest` | All 6 contact tools |
| `TransferMCPServerTest` | All 5 transfer tools |
| `DomainRegistrarMCPServerTest` | Extended domain tools (5 new) |
| `HostMCPServerTest` | All 4 host tools |
| `CatalogMCPServerTest` | All 3 catalog tools |
| `AuditMCPServerTest` | All 3 audit tools |

### 19.2 Test Patterns

```java
@QuarkusTest
class VpsHostingMCPServerTest {

    @InjectMock
    VpsBackendClient backendClient;

    @InjectMock
    AuthService authService;

    @Inject
    VpsHostingMCPServer server;

    @Test
    void listVpsPackages_success() {
        // Mock backend response
        when(backendClient.getVpsPackages())
            .thenReturn(mockPackageList());

        var result = server.listVpsPackages();

        assertTrue(result.success());
        assertFalse(result.packages().isEmpty());
    }

    @Test
    void orderVps_notAuthenticated() {
        when(authService.isAuthenticated()).thenReturn(false);

        var result = server.orderVps("pkg-1", "myserver.com", "MONTHLY", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("Authentication required"));
    }

    @Test
    void orderVps_insufficientBalance() {
        when(authService.isAuthenticated()).thenReturn(true);
        when(authService.getToken()).thenReturn("test-token");
        when(backendClient.orderVps(any(), anyString()))
            .thenThrow(new WebApplicationException(402));

        var result = server.orderVps("pkg-1", "myserver.com", "MONTHLY", null);

        assertFalse(result.success());
        assertTrue(result.message().contains("Insufficient balance"));
    }
}
```

### 19.3 Integration Tests

- Test actual MCP SSE endpoint responses
- Test auth flow end-to-end
- Test error propagation from backend to MCP result

---

## 20. OpenClaw / ClawHub Compatibility

### 20.1 SKILL.md (Create in project root)

A `SKILL.md` file should be created in the project root for OpenClaw discovery. See the companion document `SKILL.md` for the full content.

### 20.2 claw.json (Create in project root)

```json
{
  "name": "osir-registrar",
  "version": "2.0.0",
  "description": "Full-service domain registrar, VPS hosting, and server management via OSIR.COM",
  "author": "osir",
  "license": "MIT",
  "permissions": ["network"],
  "entry": "SKILL.md",
  "tags": [
    "domains", "registrar", "hosting", "vps", "dedicated-servers",
    "dns", "nameservers", "domain-registration", "web-hosting",
    "infrastructure", "devops", "billing", "invoices"
  ],
  "models": ["claude-*", "gpt-*", "gemini-*"],
  "minOpenClawVersion": "0.8.0"
}
```

### 20.3 OpenClaw MCP Plugin Configuration

Users connect via the MCP plugin in `~/.openclaw/openclaw.json`:

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

### 20.4 Transport Requirements

For OpenClaw compatibility, the MCP server should support **Streamable HTTP** transport in addition to SSE. Check for `quarkus-mcp-server-http` or equivalent when available. The current SSE transport works with Claude Code but OpenClaw's MCP plugin uses HTTP POST with JSON-RPC 2.0.

---

## 21. Deployment & Migration

### 21.1 Implementation Order

```
Phase 0 (Quick Wins)     → Expose 5 already-defined client methods as tools
Phase 1 (VPS)            → 10 new tools, VpsBackendClient, VpsHostingMCPServer
Phase 2 (Billing)        → 9 new tools, BillingBackendClient, BillingMCPServer
Phase 3 (DNS)            → 5 new tools, DnsBackendClient, DnsMCPServer
Phase 4 (Domain Enhance) → 5 new tools (quick, already have client methods)
Phase 5 (Contacts)       → 6 new tools, ContactBackendClient, ContactMCPServer
Phase 6 (Transfer V2)    → 5 new tools, TransferBackendClient, TransferMCPServer
Phase 7 (Account)        → 2 new tools
Phase 8 (Hosts)          → 4 new tools, HostBackendClient, HostMCPServer
Phase 9 (Catalog)        → 3 new tools, CatalogBackendClient, CatalogMCPServer
Phase 10 (Audit)         → 3 new tools
```

### 21.2 Backward Compatibility

- All 18 existing tools MUST remain unchanged in behavior
- Existing tool names, parameters, and return types MUST NOT change
- New tools are purely additive
- No configuration changes that break existing deployments

### 21.3 Versioning

- Current: v1.0.0
- After Phase 0 (Quick Wins): v1.1.0
- After Phase 1-2 (VPS + Billing): v1.5.0
- After all phases complete: v2.0.0

### 21.4 Build Changes (build.gradle)

No new dependencies should be needed. All backend calls use the existing Quarkus REST Client. If the Streamable HTTP transport becomes available:

```gradle
implementation 'io.quarkiverse.mcp:quarkus-mcp-server-http:VERSION'
```

---

## Appendix A: Complete Tool Reference

### All Tools Summary (Target: ~70)

| # | Tool Name | Phase | Auth | Category |
|:-:|-----------|:-----:|:---:|----------|
| 1 | `authenticateUser` | Existing | No | Auth |
| 2 | `getAuthStatus` | Existing | No | Auth |
| 3 | `logout` | Existing | No | Auth |
| 4 | `checkDomainAvailability` | Existing | No | Domain |
| 5 | `generateDomainSuggestions` | Existing | No | Domain |
| 6 | `addPrefixToDomain` | Existing | No | Domain |
| 7 | `addSuffixToDomain` | Existing | No | Domain |
| 8 | `spinDomainWords` | Existing | No | Domain |
| 9 | `bulkDomainSuggestions` | Existing | No | Domain |
| 10 | `checkKeywordAvailability` | Existing | No | Domain |
| 11 | `checkKeywordAvailabilitySummary` | Existing | No | Domain |
| 12 | `registerDomain` | Existing | Yes | Domain |
| 13 | `transferDomain` | Existing | Yes | Domain |
| 14 | `updateNameservers` | Existing | Yes | Domain |
| 15 | `getDomainInfo` | Existing | Yes | Domain |
| 16 | `listUserDomains` | Existing | Yes | Domain |
| 17 | `validateDomainName` | Existing | No | Domain |
| 18 | `suggestAlternatives` | Existing | No | Domain |
| 19 | `renewDomain` | Phase 4 | Yes | Domain |
| 20 | `lockDomain` | Phase 4 | Yes | Domain |
| 21 | `unlockDomain` | Phase 4 | Yes | Domain |
| 22 | `updateDomainAutoRenew` | Phase 4 | Yes | Domain |
| 23 | `updateDomainPrivacy` | Phase 4 | Yes | Domain |
| 24 | `listVpsPackages` | Phase 1 | No | VPS |
| 25 | `listVpsLocations` | Phase 1 | No | VPS |
| 26 | `getVpsPackageDetails` | Phase 1 | No | VPS |
| 27 | `orderVps` | Phase 1 | Yes | VPS |
| 28 | `listMyVpsInstances` | Phase 1 | Yes | VPS |
| 29 | `getVpsInstanceDetails` | Phase 1 | Yes | VPS |
| 30 | `deleteVpsInstance` | Phase 1 | Yes | VPS |
| 31 | `changeVpsPaymentTerm` | Phase 1 | Yes | VPS |
| 32 | `loginToVpsPanel` | Phase 1 | Yes | VPS |
| 33 | `countMyVpsInstances` | Phase 1 | Yes | VPS |
| 34 | `getAccountBalance` | Phase 2 | Yes | Billing |
| 35 | `listInvoices` | Phase 2 | Yes | Billing |
| 36 | `getInvoiceDetails` | Phase 2 | Yes | Billing |
| 37 | `payInvoice` | Phase 2 | Yes | Billing |
| 38 | `getInvoiceStatistics` | Phase 2 | Yes | Billing |
| 39 | `createPaymentSession` | Phase 2 | Yes | Billing |
| 40 | `getPaymentTransactions` | Phase 2 | Yes | Billing |
| 41 | `previewPaymentFees` | Phase 2 | Yes | Billing |
| 42 | `getDomainPricing` | Phase 2 | Yes | Billing |
| 43 | `listDnsRecords` | Phase 3 | Yes | DNS |
| 44 | `createDnsRecord` | Phase 3 | Yes | DNS |
| 45 | `updateDnsRecord` | Phase 3 | Yes | DNS |
| 46 | `deleteDnsRecord` | Phase 3 | Yes | DNS |
| 47 | `getDnsRecord` | Phase 3 | Yes | DNS |
| 48 | `listContacts` | Phase 5 | Yes | Contacts |
| 49 | `getContact` | Phase 5 | Yes | Contacts |
| 50 | `createContact` | Phase 5 | Yes | Contacts |
| 51 | `updateContact` | Phase 5 | Yes | Contacts |
| 52 | `deleteContact` | Phase 5 | Yes | Contacts |
| 53 | `getContactsForDomain` | Phase 5 | Yes | Contacts |
| 54 | `getTransferQuote` | Phase 6 | Yes | Transfer |
| 55 | `initiateTransfer` | Phase 6 | Yes | Transfer |
| 56 | `getTransferStatus` | Phase 6 | Yes | Transfer |
| 57 | `cancelTransfer` | Phase 6 | Yes | Transfer |
| 58 | `listPendingTransfers` | Phase 6 | Yes | Transfer |
| 59 | `getMyProfile` | Phase 7 | Yes | Account |
| 60 | `getAccountSummary` | Phase 7 | Yes | Account |
| 61 | `checkHostAvailability` | Phase 8 | Yes | Hosts |
| 62 | `createHost` | Phase 8 | Yes | Hosts |
| 63 | `getHostsForDomain` | Phase 8 | Yes | Hosts |
| 64 | `deleteHost` | Phase 8 | Yes | Hosts |
| 65 | `getProductCatalog` | Phase 9 | No | Catalog |
| 66 | `getDomainExtensions` | Phase 9 | No | Catalog |
| 67 | `getDedicatedServerCatalog` | Phase 9 | No | Catalog |
| 68 | `getDomainAuditTrail` | Phase 10 | Yes | Audit |
| 69 | `getMyAuditLogs` | Phase 10 | Yes | Audit |
| 70 | `getRecentActivity` | Phase 10 | Yes | Audit |

---

## Appendix B: Backend API Endpoint Map

### Endpoints Used by Current MCP Tools (18 tools → 27 endpoints)

| Backend Endpoint | MCP Tool |
|-----------------|----------|
| `POST /api/auth` | `authenticateUser` |
| `GET /v2/domain/{domain}/available` | `checkDomainAvailability` |
| `POST /domains` | `registerDomain` |
| `POST /domains/transfer` | `transferDomain` |
| `PUT /v2/domain/{domain}/nameservers` | `updateNameservers` |
| `GET /v1/domain/{domain}/info` | `getDomainInfo` |
| `GET /v1/domain` | `listUserDomains` |
| `GET /namesuggestions/suggest` | `generateDomainSuggestions` |
| `GET /namesuggestions/spin-word` | `spinDomainWords` |
| `GET /namesuggestions/add-prefix` | `addPrefixToDomain` |
| `GET /namesuggestions/add-suffix` | `addSuffixToDomain` |
| `POST /namesuggestions/bulk-suggest` | `bulkDomainSuggestions` |
| `GET /namesuggestions/keyword-availability/{keyword}` | `checkKeywordAvailability` |
| `GET /namesuggestions/keyword-availability/{keyword}/summary` | `checkKeywordAvailabilitySummary` |

### New Endpoints to Integrate (~50)

| Backend Endpoint | New MCP Tool | Phase |
|-----------------|-------------|:-----:|
| `POST /domains/{domain}/renew` | `renewDomain` | 4 |
| `POST /domains/{domain}/lock` | `lockDomain` | 4 |
| `DELETE /domains/{domain}/lock` | `unlockDomain` | 4 |
| `PUT /domains/{domain}/auto-renew` | `updateDomainAutoRenew` | 4 |
| `PUT /domains/{domain}/privacy` | `updateDomainPrivacy` | 4 |
| `GET /v1/public/catalog/vps` | `listVpsPackages` | 1 |
| `GET /v1/public/catalog/vps/locations` | `listVpsLocations` | 1 |
| `GET /v1/hosting/vps/packages/{id}` | `getVpsPackageDetails` | 1 |
| `POST /v1/hosting/vps/order` | `orderVps` | 1 |
| `GET /v1/hosting/vps/instances` | `listMyVpsInstances` | 1 |
| `GET /v1/hosting/vps/instances/{id}` | `getVpsInstanceDetails` | 1 |
| `POST /v1/hosting/vps/instances/{id}/delete` | `deleteVpsInstance` | 1 |
| `POST /v1/hosting/vps/instances/{id}/change-payment-term` | `changeVpsPaymentTerm` | 1 |
| `POST /v1/hosting/vps/instances/{id}/login` | `loginToVpsPanel` | 1 |
| `GET /v1/hosting/vps/instances/count` | `countMyVpsInstances` | 1 |
| `GET /v1/payment/balance` | `getAccountBalance` | 2 |
| `GET /v1/billing/invoices` | `listInvoices` | 2 |
| `GET /v1/billing/invoices/{id}` | `getInvoiceDetails` | 2 |
| `POST /v1/billing/invoices/{id}/pay` | `payInvoice` | 2 |
| `GET /v1/billing/invoices/statistics` | `getInvoiceStatistics` | 2 |
| `POST /v1/payment/checkout-session` | `createPaymentSession` | 2 |
| `GET /v1/payment/transactions` | `getPaymentTransactions` | 2 |
| `GET /v1/payment/fee-preview` | `previewPaymentFees` | 2 |
| `GET /pricing/domains` | `getDomainPricing` | 2 |
| `GET /dns/domains/{domain}/records` | `listDnsRecords` | 3 |
| `POST /dns/domains/{domain}/records` | `createDnsRecord` | 3 |
| `PUT /dns/domains/{domain}/records/{id}` | `updateDnsRecord` | 3 |
| `DELETE /dns/domains/{domain}/records/{id}` | `deleteDnsRecord` | 3 |
| `GET /dns/domains/{domain}/records/{id}` | `getDnsRecord` | 3 |
| `GET /v1/contacts` | `listContacts` | 5 |
| `GET /v1/contacts/{id}` | `getContact` | 5 |
| `POST /v1/contacts` | `createContact` | 5 |
| `PUT /v1/contacts/{id}` | `updateContact` | 5 |
| `DELETE /v1/contacts/{id}` | `deleteContact` | 5 |
| `GET /v1/contacts/for-domain/{id}/all` | `getContactsForDomain` | 5 |
| `GET /v2/transfer/{domain}/quote` | `getTransferQuote` | 6 |
| `POST /v2/transfer/initiate` | `initiateTransfer` | 6 |
| `GET /v2/transfer/{domain}/status` | `getTransferStatus` | 6 |
| `POST /v2/transfer/{domain}/cancel` | `cancelTransfer` | 6 |
| `GET /v2/transfer/gaining/pending` | `listPendingTransfers` | 6 |
| `GET /v1/customers/me` | `getMyProfile` | 7 |
| *Composite* | `getAccountSummary` | 7 |
| `POST /v2/hosts/check` | `checkHostAvailability` | 8 |
| `POST /v2/hosts` | `createHost` | 8 |
| `GET /v2/hosts/domain/{domain}` | `getHostsForDomain` | 8 |
| `DELETE /v2/hosts/{hostname}` | `deleteHost` | 8 |
| `GET /v1/public/catalog` | `getProductCatalog` | 9 |
| `GET /v1/public/catalog/domains` | `getDomainExtensions` | 9 |
| `GET /v1/public/catalog/dedicated` | `getDedicatedServerCatalog` | 9 |
| `GET /v1/audit/domain/{domain}` | `getDomainAuditTrail` | 10 |
| `GET /v1/audit/customer/{id}` | `getMyAuditLogs` | 10 |
| `GET /v1/audit/recent` | `getRecentActivity` | 10 |

---

*End of Specification*
