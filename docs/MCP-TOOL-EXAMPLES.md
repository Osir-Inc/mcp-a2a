# OSIR MCP Server - Tool Interaction Examples

**Server:** `http://localhost:8081` (dev) / `https://your-server:8081` (prod)
**Transport:** SSE at `/mcp`
**Protocol:** MCP (Model Context Protocol) over JSON-RPC 2.0

---

## Table of Contents

1. [Connection & Protocol](#1-connection--protocol)
2. [Authentication](#2-authentication)
3. [Domain Management](#3-domain-management)
4. [Domain Search & Suggestions](#4-domain-search--suggestions)
5. [DNS Management](#5-dns-management)
6. [VPS Hosting](#6-vps-hosting)
7. [Billing & Payments](#7-billing--payments)
8. [Contact Management](#8-contact-management)
9. [Transfer V2](#9-transfer-v2)
10. [Host/Glue Records](#10-hostglue-records)
11. [Product Catalog (Public)](#11-product-catalog-public)
12. [Account & Profile](#12-account--profile)
13. [Audit & Reporting](#13-audit--reporting)

---

## 1. Connection & Protocol

### How MCP SSE Works

1. **Open an SSE connection** to get a session endpoint
2. **Send JSON-RPC messages** to the returned endpoint
3. **Receive responses** through the SSE stream

### Step 1: Open SSE Connection

```bash
curl -N http://localhost:8081/mcp/sse
```

The server responds with an SSE event containing your message endpoint:

```
event: endpoint
data: /mcp/messages/abc123-session-id
```

### Step 2: Initialize the Session

```bash
curl -X POST http://localhost:8081/mcp/messages/abc123-session-id \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 1,
    "method": "initialize",
    "params": {
      "protocolVersion": "2024-11-05",
      "capabilities": {},
      "clientInfo": { "name": "manual-test", "version": "1.0.0" }
    }
  }'
```

### Step 3: List Available Tools

```bash
curl -X POST http://localhost:8081/mcp/messages/abc123-session-id \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 2,
    "method": "tools/list",
    "params": {}
  }'
```

### Step 4: Call a Tool

All tool calls use the same format:

```bash
curl -X POST http://localhost:8081/mcp/messages/abc123-session-id \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": 3,
    "method": "tools/call",
    "params": {
      "name": "toolName",
      "arguments": {
        "param1": "value1",
        "param2": "value2"
      }
    }
  }'
```

> **Note:** In all examples below, replace `SESSION_URL` with `http://localhost:8081/mcp/messages/<your-session-id>`. The session ID comes from Step 1.

---

## 2. Authentication

Most tools require authentication. Two login methods are available:

- **Device Authorization Flow (preferred)** -- Secure browser-based login supporting MFA and SSO via KeyCloak. The user never shares their password with the MCP server.
- **Password Login (fallback)** -- Direct username/password authentication via the backend.

Token refresh is automatic: when a token is within 60 seconds of expiry, `getCurrentToken()` silently refreshes it using the refresh token.

### loginWithDevice (preferred)

Starts the OAuth 2.0 Device Authorization Grant (RFC 8628). Returns a verification URL and user code. The user opens the URL in their browser, enters the code, and authenticates with KeyCloak.

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 1, "method": "tools/call",
  "params": { "name": "loginWithDevice", "arguments": {} }
}'
```

**Response:**
```json
{
  "success": true,
  "message": "Please visit the verification URL and enter the user code to authenticate.",
  "deviceCode": "aBcDeFgHiJkLmNoPq",
  "userCode": "ABCD-EFGH",
  "verificationUri": "https://auth.osir.com/realms/osir/device",
  "verificationUriComplete": "https://auth.osir.com/realms/osir/device?user_code=ABCD-EFGH",
  "expiresIn": 600,
  "interval": 5
}
```

### checkDeviceLoginStatus

After the user has been directed to the verification URL, poll this tool to check if they have completed authentication. Use the `deviceCode` returned by `loginWithDevice`.

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 2, "method": "tools/call",
  "params": {
    "name": "checkDeviceLoginStatus",
    "arguments": { "deviceCode": "aBcDeFgHiJkLmNoPq" }
  }
}'
```

**Possible statuses:** `pending` (user hasn't authorized yet), `complete` (authenticated -- session is now active), `expired` (device code timed out), `denied` (user rejected), `slow_down` (polling too fast).

### authenticateUser (fallback)

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 3, "method": "tools/call",
  "params": {
    "name": "authenticateUser",
    "arguments": {
      "username": "your_username",
      "password": "your_password"
    }
  }
}'
```

### getAuthStatus

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 4, "method": "tools/call",
  "params": { "name": "getAuthStatus", "arguments": {} }
}'
```

### logout

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 5, "method": "tools/call",
  "params": { "name": "logout", "arguments": {} }
}'
```

---

## 3. Domain Management

### checkDomainAvailability

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 10, "method": "tools/call",
  "params": {
    "name": "checkDomainAvailability",
    "arguments": { "domain": "example.com" }
  }
}'
```

### validateDomainName

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 11, "method": "tools/call",
  "params": {
    "name": "validateDomainName",
    "arguments": { "domain": "my-domain.com" }
  }
}'
```

### getDomainInfo

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 12, "method": "tools/call",
  "params": {
    "name": "getDomainInfo",
    "arguments": { "domain": "example.com" }
  }
}'
```

### listUserDomains

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 13, "method": "tools/call",
  "params": { "name": "listUserDomains", "arguments": {} }
}'
```

### registerDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 14, "method": "tools/call",
  "params": {
    "name": "registerDomain",
    "arguments": {
      "domain": "mynewdomain.com",
      "years": 1,
      "registrantInfo": {
        "firstName": "John",
        "lastName": "Doe",
        "email": "john@example.com",
        "phone": "+1.5551234567",
        "address": {
          "street1": "123 Main St",
          "city": "Springfield",
          "state": "IL",
          "postalCode": "62701",
          "country": "US"
        }
      },
      "nameservers": ["ns1.example.com", "ns2.example.com"],
      "privacyProtection": true,
      "autoRenew": true
    }
  }
}'
```

### updateNameservers

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 15, "method": "tools/call",
  "params": {
    "name": "updateNameservers",
    "arguments": {
      "domain": "example.com",
      "nameservers": ["ns1.cloudflare.com", "ns2.cloudflare.com"]
    }
  }
}'
```

### renewDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 16, "method": "tools/call",
  "params": {
    "name": "renewDomain",
    "arguments": { "domain": "example.com", "years": 2 }
  }
}'
```

### lockDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 17, "method": "tools/call",
  "params": {
    "name": "lockDomain",
    "arguments": { "domain": "example.com" }
  }
}'
```

### unlockDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 18, "method": "tools/call",
  "params": {
    "name": "unlockDomain",
    "arguments": { "domain": "example.com" }
  }
}'
```

### updateDomainAutoRenew

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 19, "method": "tools/call",
  "params": {
    "name": "updateDomainAutoRenew",
    "arguments": { "domain": "example.com", "enabled": true }
  }
}'
```

### updateDomainPrivacy

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 20, "method": "tools/call",
  "params": {
    "name": "updateDomainPrivacy",
    "arguments": { "domain": "example.com", "enabled": true }
  }
}'
```

### transferDomain (legacy v1)

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 21, "method": "tools/call",
  "params": {
    "name": "transferDomain",
    "arguments": {
      "domain": "example.com",
      "authCode": "EPP-AUTH-CODE-HERE",
      "registrantInfo": {
        "firstName": "John",
        "lastName": "Doe",
        "email": "john@example.com",
        "phone": "+1.5551234567",
        "address": {
          "street1": "123 Main St",
          "city": "Springfield",
          "state": "IL",
          "postalCode": "62701",
          "country": "US"
        }
      }
    }
  }
}'
```

---

## 4. Domain Search & Suggestions

### suggestAlternatives

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 30, "method": "tools/call",
  "params": {
    "name": "suggestAlternatives",
    "arguments": { "domain": "example.com", "limit": 10 }
  }
}'
```

### generateDomainSuggestions

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 31, "method": "tools/call",
  "params": {
    "name": "generateDomainSuggestions",
    "arguments": {
      "name": "techstartup",
      "tlds": "com,net,io",
      "lang": "eng",
      "useNumbers": false,
      "maxResults": 20
    }
  }
}'
```

### spinDomainWords

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 32, "method": "tools/call",
  "params": {
    "name": "spinDomainWords",
    "arguments": {
      "name": "pizza,restaurant",
      "position": 0,
      "similarity": 0.7,
      "tlds": "com,net",
      "lang": "eng",
      "maxResults": 15
    }
  }
}'
```

### addPrefixToDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 33, "method": "tools/call",
  "params": {
    "name": "addPrefixToDomain",
    "arguments": {
      "name": "hosting",
      "vocabulary": "@prefixes",
      "tlds": "com,net",
      "lang": "eng",
      "maxResults": 20
    }
  }
}'
```

### addSuffixToDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 34, "method": "tools/call",
  "params": {
    "name": "addSuffixToDomain",
    "arguments": {
      "name": "cloud",
      "vocabulary": "@suffixes",
      "tlds": "com,io",
      "lang": "eng",
      "maxResults": 20
    }
  }
}'
```

### bulkDomainSuggestions

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 35, "method": "tools/call",
  "params": {
    "name": "bulkDomainSuggestions",
    "arguments": {
      "keywords": ["host", "vps", "server"],
      "tlds": "com,net,tech",
      "lang": "eng",
      "maxResults": 20
    }
  }
}'
```

### checkKeywordAvailability

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 36, "method": "tools/call",
  "params": {
    "name": "checkKeywordAvailability",
    "arguments": {
      "keyword": "cloudhost",
      "registries": "verisign,pir,centralnic",
      "tlds": "com,net,org"
    }
  }
}'
```

### checkKeywordAvailabilitySummary

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 37, "method": "tools/call",
  "params": {
    "name": "checkKeywordAvailabilitySummary",
    "arguments": {
      "keyword": "cloudhost",
      "registries": "verisign,pir",
      "tlds": "com,net"
    }
  }
}'
```

---

## 5. DNS Management

### listDnsRecords

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 40, "method": "tools/call",
  "params": {
    "name": "listDnsRecords",
    "arguments": { "domain": "example.com" }
  }
}'
```

### createDnsRecord

```bash
# A record
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 41, "method": "tools/call",
  "params": {
    "name": "createDnsRecord",
    "arguments": {
      "domain": "example.com",
      "name": "www",
      "type": "A",
      "content": "93.184.216.34",
      "ttl": 3600
    }
  }
}'

# MX record
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 42, "method": "tools/call",
  "params": {
    "name": "createDnsRecord",
    "arguments": {
      "domain": "example.com",
      "name": "@",
      "type": "MX",
      "content": "mail.example.com",
      "ttl": 3600,
      "priority": 10
    }
  }
}'

# TXT record (SPF)
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 43, "method": "tools/call",
  "params": {
    "name": "createDnsRecord",
    "arguments": {
      "domain": "example.com",
      "name": "@",
      "type": "TXT",
      "content": "v=spf1 include:_spf.google.com ~all",
      "ttl": 3600
    }
  }
}'

# CNAME record
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 44, "method": "tools/call",
  "params": {
    "name": "createDnsRecord",
    "arguments": {
      "domain": "example.com",
      "name": "blog",
      "type": "CNAME",
      "content": "blog.example.com",
      "ttl": 3600
    }
  }
}'
```

### updateDnsRecord

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 45, "method": "tools/call",
  "params": {
    "name": "updateDnsRecord",
    "arguments": {
      "domain": "example.com",
      "recordId": "rec-123",
      "content": "198.51.100.1",
      "ttl": 7200
    }
  }
}'
```

### getDnsRecord

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 46, "method": "tools/call",
  "params": {
    "name": "getDnsRecord",
    "arguments": {
      "domain": "example.com",
      "recordId": "rec-123"
    }
  }
}'
```

### deleteDnsRecord

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 47, "method": "tools/call",
  "params": {
    "name": "deleteDnsRecord",
    "arguments": {
      "domain": "example.com",
      "recordId": "rec-123"
    }
  }
}'
```

---

## 6. VPS Hosting

### listVpsPackages (no auth)

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 50, "method": "tools/call",
  "params": { "name": "listVpsPackages", "arguments": {} }
}'
```

### listVpsLocations (no auth)

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 51, "method": "tools/call",
  "params": { "name": "listVpsLocations", "arguments": {} }
}'
```

### getVpsPackageDetails

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 52, "method": "tools/call",
  "params": {
    "name": "getVpsPackageDetails",
    "arguments": { "packageId": "vps-starter-1" }
  }
}'
```

### orderVps

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 53, "method": "tools/call",
  "params": {
    "name": "orderVps",
    "arguments": {
      "packageId": "vps-starter-1",
      "hostname": "myserver.example.com",
      "paymentTerm": "MONTHLY",
      "operatingSystem": "ubuntu-22.04"
    }
  }
}'
```

### listMyVpsInstances

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 54, "method": "tools/call",
  "params": { "name": "listMyVpsInstances", "arguments": {} }
}'
```

### getVpsInstanceDetails

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 55, "method": "tools/call",
  "params": {
    "name": "getVpsInstanceDetails",
    "arguments": { "instanceId": "vps-inst-abc123" }
  }
}'
```

### changeVpsPaymentTerm

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 56, "method": "tools/call",
  "params": {
    "name": "changeVpsPaymentTerm",
    "arguments": {
      "instanceId": "vps-inst-abc123",
      "paymentTerm": "ANNUAL"
    }
  }
}'
```

### loginToVpsPanel

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 57, "method": "tools/call",
  "params": {
    "name": "loginToVpsPanel",
    "arguments": { "instanceId": "vps-inst-abc123" }
  }
}'
```

### countMyVpsInstances

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 58, "method": "tools/call",
  "params": { "name": "countMyVpsInstances", "arguments": {} }
}'
```

### deleteVpsInstance

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 59, "method": "tools/call",
  "params": {
    "name": "deleteVpsInstance",
    "arguments": { "instanceId": "vps-inst-abc123" }
  }
}'
```

---

## 7. Billing & Payments

### getAccountBalance

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 60, "method": "tools/call",
  "params": { "name": "getAccountBalance", "arguments": {} }
}'
```

### listInvoices

```bash
# All invoices
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 61, "method": "tools/call",
  "params": {
    "name": "listInvoices",
    "arguments": { "page": 0, "size": 20 }
  }
}'

# Only pending invoices
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 62, "method": "tools/call",
  "params": {
    "name": "listInvoices",
    "arguments": { "status": "PENDING", "page": 0, "size": 10 }
  }
}'
```

### getInvoiceDetails

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 63, "method": "tools/call",
  "params": {
    "name": "getInvoiceDetails",
    "arguments": { "invoiceId": "INV-2026-001" }
  }
}'
```

### payInvoice

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 64, "method": "tools/call",
  "params": {
    "name": "payInvoice",
    "arguments": { "invoiceId": "INV-2026-001" }
  }
}'
```

### getInvoiceStatistics

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 65, "method": "tools/call",
  "params": { "name": "getInvoiceStatistics", "arguments": {} }
}'
```

### createPaymentSession

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 66, "method": "tools/call",
  "params": {
    "name": "createPaymentSession",
    "arguments": { "amount": 50.00, "currency": "USD" }
  }
}'
```

### getPaymentTransactions

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 67, "method": "tools/call",
  "params": {
    "name": "getPaymentTransactions",
    "arguments": { "page": 0, "size": 20 }
  }
}'
```

### previewPaymentFees

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 68, "method": "tools/call",
  "params": {
    "name": "previewPaymentFees",
    "arguments": { "amount": 100.00, "currency": "USD" }
  }
}'
```

### getDomainPricing

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 69, "method": "tools/call",
  "params": {
    "name": "getDomainPricing",
    "arguments": { "tld": "com", "operation": "register" }
  }
}'
```

---

## 8. Contact Management

### listContacts

```bash
# All contacts
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 70, "method": "tools/call",
  "params": { "name": "listContacts", "arguments": {} }
}'

# Search contacts
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 71, "method": "tools/call",
  "params": {
    "name": "listContacts",
    "arguments": { "search": "john" }
  }
}'
```

### getContact

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 72, "method": "tools/call",
  "params": {
    "name": "getContact",
    "arguments": { "contactId": "c-12345" }
  }
}'
```

### createContact

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 73, "method": "tools/call",
  "params": {
    "name": "createContact",
    "arguments": {
      "firstName": "John",
      "lastName": "Doe",
      "email": "john@example.com",
      "phone": "+1.5551234567",
      "organization": "OSIR Inc",
      "street1": "123 Main St",
      "city": "Springfield",
      "state": "IL",
      "postalCode": "62701",
      "country": "US"
    }
  }
}'
```

### updateContact

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 74, "method": "tools/call",
  "params": {
    "name": "updateContact",
    "arguments": {
      "contactId": "c-12345",
      "firstName": "Jane",
      "email": "jane@example.com"
    }
  }
}'
```

### deleteContact

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 75, "method": "tools/call",
  "params": {
    "name": "deleteContact",
    "arguments": { "contactId": "c-12345" }
  }
}'
```

### getContactsForDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 76, "method": "tools/call",
  "params": {
    "name": "getContactsForDomain",
    "arguments": { "domainId": "dom-abc123" }
  }
}'
```

---

## 9. Transfer V2

### getTransferQuote

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 80, "method": "tools/call",
  "params": {
    "name": "getTransferQuote",
    "arguments": { "domain": "example.com" }
  }
}'
```

### initiateTransfer

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 81, "method": "tools/call",
  "params": {
    "name": "initiateTransfer",
    "arguments": {
      "domain": "example.com",
      "authCode": "EPP-AUTH-CODE-FROM-CURRENT-REGISTRAR"
    }
  }
}'
```

### getTransferStatus

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 82, "method": "tools/call",
  "params": {
    "name": "getTransferStatus",
    "arguments": { "domain": "example.com" }
  }
}'
```

### cancelTransfer

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 83, "method": "tools/call",
  "params": {
    "name": "cancelTransfer",
    "arguments": { "domain": "example.com" }
  }
}'
```

### listPendingTransfers

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 84, "method": "tools/call",
  "params": { "name": "listPendingTransfers", "arguments": {} }
}'
```

---

## 10. Host/Glue Records

### checkHostAvailability

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 90, "method": "tools/call",
  "params": {
    "name": "checkHostAvailability",
    "arguments": { "hostname": "ns1.example.com" }
  }
}'
```

### createHost

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 91, "method": "tools/call",
  "params": {
    "name": "createHost",
    "arguments": {
      "hostname": "ns1.example.com",
      "ipAddresses": ["192.0.2.1", "198.51.100.1"]
    }
  }
}'
```

### getHostsForDomain

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 92, "method": "tools/call",
  "params": {
    "name": "getHostsForDomain",
    "arguments": { "domain": "example.com" }
  }
}'
```

### deleteHost

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 93, "method": "tools/call",
  "params": {
    "name": "deleteHost",
    "arguments": { "hostname": "ns1.example.com" }
  }
}'
```

---

## 11. Product Catalog (Public)

These tools do NOT require authentication.

### getProductCatalog

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 100, "method": "tools/call",
  "params": { "name": "getProductCatalog", "arguments": {} }
}'
```

### getDomainExtensions

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 101, "method": "tools/call",
  "params": { "name": "getDomainExtensions", "arguments": {} }
}'
```

### getDedicatedServerCatalog

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 102, "method": "tools/call",
  "params": { "name": "getDedicatedServerCatalog", "arguments": {} }
}'
```

---

## 12. Account & Profile

### getMyProfile

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 110, "method": "tools/call",
  "params": { "name": "getMyProfile", "arguments": {} }
}'
```

### getAccountSummary

Returns aggregated data from profile, balance, VPS count, and pending transfers.

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 111, "method": "tools/call",
  "params": { "name": "getAccountSummary", "arguments": {} }
}'
```

---

## 13. Audit & Reporting

### getDomainAuditTrail

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 120, "method": "tools/call",
  "params": {
    "name": "getDomainAuditTrail",
    "arguments": { "domain": "example.com" }
  }
}'
```

### getMyAuditLogs

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 121, "method": "tools/call",
  "params": {
    "name": "getMyAuditLogs",
    "arguments": { "page": 0, "size": 50 }
  }
}'
```

### getRecentActivity

```bash
curl -X POST $SESSION_URL -H "Content-Type: application/json" -d '{
  "jsonrpc": "2.0", "id": 122, "method": "tools/call",
  "params": { "name": "getRecentActivity", "arguments": {} }
}'
```

---

## Quick Reference: All 73 Tools

| # | Tool | Auth | Category |
|---|------|:----:|----------|
| 1 | `loginWithDevice` | No | Auth |
| 2 | `checkDeviceLoginStatus` | No | Auth |
| 3 | `authenticateUser` | No | Auth |
| 4 | `getAuthStatus` | No | Auth |
| 5 | `logout` | No | Auth |
| 6 | `checkDomainAvailability` | Yes | Domain |
| 7 | `registerDomain` | Yes | Domain |
| 8 | `transferDomain` | Yes | Domain |
| 9 | `updateNameservers` | Yes | Domain |
| 10 | `getDomainInfo` | Yes | Domain |
| 11 | `listUserDomains` | Yes | Domain |
| 12 | `renewDomain` | Yes | Domain |
| 13 | `lockDomain` | Yes | Domain |
| 14 | `unlockDomain` | Yes | Domain |
| 15 | `updateDomainAutoRenew` | Yes | Domain |
| 16 | `updateDomainPrivacy` | Yes | Domain |
| 17 | `validateDomainName` | No | Domain |
| 18 | `suggestAlternatives` | No | Search |
| 19 | `generateDomainSuggestions` | No | Search |
| 20 | `spinDomainWords` | No | Search |
| 21 | `addPrefixToDomain` | No | Search |
| 22 | `addSuffixToDomain` | No | Search |
| 23 | `bulkDomainSuggestions` | No | Search |
| 24 | `checkKeywordAvailability` | No | Search |
| 25 | `checkKeywordAvailabilitySummary` | No | Search |
| 26 | `listDnsRecords` | Yes | DNS |
| 27 | `createDnsRecord` | Yes | DNS |
| 28 | `updateDnsRecord` | Yes | DNS |
| 29 | `deleteDnsRecord` | Yes | DNS |
| 30 | `getDnsRecord` | Yes | DNS |
| 31 | `listVpsPackages` | No | VPS |
| 32 | `listVpsLocations` | No | VPS |
| 33 | `getVpsPackageDetails` | No | VPS |
| 34 | `orderVps` | Yes | VPS |
| 35 | `listMyVpsInstances` | Yes | VPS |
| 36 | `getVpsInstanceDetails` | Yes | VPS |
| 37 | `deleteVpsInstance` | Yes | VPS |
| 38 | `changeVpsPaymentTerm` | Yes | VPS |
| 39 | `loginToVpsPanel` | Yes | VPS |
| 40 | `countMyVpsInstances` | Yes | VPS |
| 41 | `getAccountBalance` | Yes | Billing |
| 42 | `listInvoices` | Yes | Billing |
| 43 | `getInvoiceDetails` | Yes | Billing |
| 44 | `payInvoice` | Yes | Billing |
| 45 | `getInvoiceStatistics` | Yes | Billing |
| 46 | `createPaymentSession` | Yes | Billing |
| 47 | `getPaymentTransactions` | Yes | Billing |
| 48 | `previewPaymentFees` | Yes | Billing |
| 49 | `getDomainPricing` | Yes | Billing |
| 50 | `listContacts` | Yes | Contact |
| 51 | `getContact` | Yes | Contact |
| 52 | `createContact` | Yes | Contact |
| 53 | `updateContact` | Yes | Contact |
| 54 | `deleteContact` | Yes | Contact |
| 55 | `getContactsForDomain` | Yes | Contact |
| 56 | `getTransferQuote` | Yes | Transfer |
| 57 | `initiateTransfer` | Yes | Transfer |
| 58 | `getTransferStatus` | Yes | Transfer |
| 59 | `cancelTransfer` | Yes | Transfer |
| 60 | `listPendingTransfers` | Yes | Transfer |
| 61 | `checkHostAvailability` | Yes | Host |
| 62 | `createHost` | Yes | Host |
| 63 | `getHostsForDomain` | Yes | Host |
| 64 | `deleteHost` | Yes | Host |
| 65 | `getProductCatalog` | No | Catalog |
| 66 | `getDomainExtensions` | No | Catalog |
| 67 | `getDedicatedServerCatalog` | No | Catalog |
| 68 | `getMyProfile` | Yes | Account |
| 69 | `getAccountSummary` | Yes | Account |
| 70 | `getDomainAuditTrail` | Yes | Audit |
| 71 | `getMyAuditLogs` | Yes | Audit |
| 72 | `getRecentActivity` | Yes | Audit |
| 73 | DomainRegistrarMCPServer has 26 `@Tool` methods (tools #1-25 above) | - | - |

---

## Scripted Workflow Examples

### Device Flow Login (preferred)

```bash
#!/bin/bash
BASE_URL="http://localhost:8081"

# 1. Open SSE and capture session endpoint
echo "Connecting to MCP server..."
SESSION_PATH=$(curl -s -N "$BASE_URL/mcp/sse" 2>&1 | head -2 | grep "data:" | sed 's/data: //')
SESSION_URL="$BASE_URL$SESSION_PATH"
echo "Session: $SESSION_URL"

# 2. Initialize
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":1,"method":"initialize",
  "params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"script","version":"1.0"}}
}'

# 3. Start device login
RESPONSE=$(curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":2,"method":"tools/call",
  "params":{"name":"loginWithDevice","arguments":{}}
}')
echo "$RESPONSE"
# Extract deviceCode and verificationUriComplete from the response,
# then open the URL in a browser and enter the user code.

# 4. Poll for completion (repeat until status is "complete")
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":3,"method":"tools/call",
  "params":{"name":"checkDeviceLoginStatus","arguments":{"deviceCode":"DEVICE_CODE_HERE"}}
}'

# 5. Once authenticated, use any tool
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":4,"method":"tools/call",
  "params":{"name":"listUserDomains","arguments":{}}
}'
```

### Password Login (fallback)

```bash
#!/bin/bash
BASE_URL="http://localhost:8081"

# 1. Open SSE and capture session endpoint
echo "Connecting to MCP server..."
SESSION_PATH=$(curl -s -N "$BASE_URL/mcp/sse" 2>&1 | head -2 | grep "data:" | sed 's/data: //')
SESSION_URL="$BASE_URL$SESSION_PATH"
echo "Session: $SESSION_URL"

# 2. Initialize
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":1,"method":"initialize",
  "params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"script","version":"1.0"}}
}'

# 3. Authenticate with password
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":2,"method":"tools/call",
  "params":{"name":"authenticateUser","arguments":{"username":"myuser","password":"mypass"}}
}'

# 4. Check a domain
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":3,"method":"tools/call",
  "params":{"name":"checkDomainAvailability","arguments":{"domain":"example.com"}}
}'

# 5. List my domains
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":4,"method":"tools/call",
  "params":{"name":"listUserDomains","arguments":{}}
}'

# 6. Get account summary
curl -s -X POST "$SESSION_URL" -H "Content-Type: application/json" -d '{
  "jsonrpc":"2.0","id":5,"method":"tools/call",
  "params":{"name":"getAccountSummary","arguments":{}}
}'
```
