package com.osir.mcp;

import com.osir.mcp.security.McpAudited;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.TextContent;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * MCP Prompts providing guided workflows and best practices.
 */
@McpAudited
@ApplicationScoped
public class PromptsMCPServer {

    @Prompt(name = "getting_started")
    public PromptMessage gettingStarted() {
        return PromptMessage.withUserRole(new TextContent("""
            Getting Started with OSIR Domain Registrar

            1. AUTHENTICATE
               Call loginWithDevice() to start secure browser-based login.
               Open the returned URL, enter the code, and complete authentication.
               Then call checkDeviceLoginStatus(deviceCode) to finalize.

            2. EXPLORE DOMAINS
               - checkDomainAvailability("yourdomain.com") to check availability
               - generateDomainSuggestions("keyword") for AI-powered suggestions
               - getDomainPricing("com") to see pricing by extension

            3. REGISTER A DOMAIN
               - registerDomain("yourdomain.com", 1, registrantInfo, nameservers)
               - Enable privacy protection and auto-renewal for best practices

            4. CONFIGURE DNS
               - listDnsRecords("yourdomain.com") to see current records
               - createDnsRecord("yourdomain.com", "www", "A", "1.2.3.4", 3600)

            5. MANAGE YOUR PORTFOLIO
               - listUserDomains() to see all your domains
               - getDomainInfo("yourdomain.com") for details on any domain
               - getAccountBalance() to check your account balance

            Tips:
            - Always enable WHOIS privacy protection for personal domains
            - Set up auto-renewal to prevent accidental expiration
            - Use at least 2 nameservers for DNS redundancy
            """));
    }

    @Prompt(name = "vps_setup_guide")
    public PromptMessage vpsSetupGuide() {
        return PromptMessage.withUserRole(new TextContent("""
            VPS Setup Guide — Step by Step

            1. BROWSE OPTIONS
               - listVpsPackages() to see available plans (CPU, RAM, storage, price)
               - listVpsLocations() to see datacenter locations
               - getVpsPackageDetails(packageId) for full specs

            2. ORDER A VPS
               - orderVps(packageId, "myhostname.example.com", "monthly", "ubuntu-22.04")
               - Payment terms: monthly, quarterly, semi-annually, annually
               - Common OS options: ubuntu-22.04, debian-12, centos-stream-9, rocky-9

            3. ACCESS YOUR VPS
               - listMyVpsInstances() to see your servers
               - loginToVpsPanel(instanceId) to get the control panel URL
               - Use the panel for console access, reinstalls, and snapshots

            4. MANAGE
               - getVpsInstanceDetails(instanceId) for status and IP info
               - changeVpsPaymentTerm(instanceId, "annually") for discounts
               - deleteVpsInstance(instanceId) to terminate (irreversible!)

            5. CONNECT YOUR DOMAIN
               After provisioning, point your domain's DNS:
               - Create an A record: createDnsRecord("yourdomain.com", "@", "A", "YOUR_VPS_IP", 3600)
               - Create www CNAME: createDnsRecord("yourdomain.com", "www", "CNAME", "yourdomain.com", 3600)
            """));
    }

    @Prompt(name = "dns_setup_guide")
    public PromptMessage dnsSetupGuide() {
        return PromptMessage.withUserRole(new TextContent("""
            DNS Setup Guide

            COMMON RECORD TYPES
            - A:     Points a name to an IPv4 address (e.g., @ -> 203.0.113.1)
            - AAAA:  Points a name to an IPv6 address
            - CNAME: Alias one name to another (e.g., www -> yourdomain.com)
            - MX:    Mail server (e.g., @ -> mail.provider.com, priority 10)
            - TXT:   Text records for SPF, DKIM, domain verification
            - SRV:   Service discovery records
            - NS:    Nameserver delegation

            TYPICAL WEBSITE SETUP
            1. createDnsRecord("domain.com", "@", "A", "YOUR_SERVER_IP", 3600)
            2. createDnsRecord("domain.com", "www", "CNAME", "domain.com", 3600)

            EMAIL SETUP (Gmail/Google Workspace)
            1. createDnsRecord("domain.com", "@", "MX", "aspmx.l.google.com", 3600, 1)
            2. createDnsRecord("domain.com", "@", "MX", "alt1.aspmx.l.google.com", 3600, 5)
            3. createDnsRecord("domain.com", "@", "TXT", "v=spf1 include:_spf.google.com ~all", 3600)

            TIPS
            - TTL of 3600 (1 hour) is standard; use 300 (5 min) during migrations
            - Always add SPF and DKIM TXT records to prevent email spoofing
            - Use listDnsRecords("domain.com") to verify your setup
            - Changes propagate globally within the TTL period
            """));
    }

    @Prompt(name = "billing_overview")
    public PromptMessage billingOverview() {
        return PromptMessage.withUserRole(new TextContent("""
            Billing & Payments Overview

            CHECK YOUR BALANCE
            - getAccountBalance() shows your current prepaid balance

            ADD FUNDS
            - createPaymentSession(amount, "USD") creates a Stripe checkout link
            - previewPaymentFees(amount, "USD") shows fees before paying

            INVOICES
            - listInvoices() shows all invoices (filter by status: PENDING, PAID, OVERDUE)
            - getInvoiceDetails(invoiceId) for line items and breakdown
            - payInvoice(invoiceId) pays from your account balance
            - getInvoiceStatistics() for summary totals

            PRICING
            - getDomainPricing("com") shows registration/renewal/transfer prices
            - Prices vary by TLD (.com, .net, .org, .io, .al, etc.)

            PAYMENT HISTORY
            - getPaymentTransactions() shows your transaction history

            TIPS
            - Keep a positive balance to avoid service interruptions
            - Enable auto-renewal on domains to prevent accidental expiration
            - Annual payment terms on VPS typically offer 10-20% discount
            """));
    }

    @Prompt(name = "domain_management_guide")
    public PromptMessage domainManagementGuide() {
        return PromptMessage.withUserRole(new TextContent("""
            Domain Management Best Practices

            SECURITY
            - lockDomain("domain.com") — prevents unauthorized transfers
            - updateDomainPrivacy("domain.com", true) — hides personal info from WHOIS
            - Keep authorization/EPP codes confidential

            RENEWAL
            - updateDomainAutoRenew("domain.com", true) — prevents accidental expiration
            - renewDomain("domain.com", 2) — manually renew for 1-10 years
            - Domains in redemption period cost significantly more to recover

            NAMESERVERS
            - updateNameservers("domain.com", ["ns1.example.com", "ns2.example.com"])
            - Always use at least 2 nameservers for redundancy
            - Nameserver changes can take up to 48 hours to fully propagate

            TRANSFERS
            - Unlock domain first: unlockDomain("domain.com")
            - Get EPP/auth code from current registrar
            - transferDomain("domain.com", "AUTH_CODE", registrantInfo)
            - Transfers typically take 5-7 days
            - Domain must be 60+ days old and not expiring within 30 days

            MONITORING
            - listUserDomains() — review all domains regularly
            - getDomainInfo("domain.com") — check expiry dates and status
            """));
    }

    @Prompt(name = "hosting_comparison")
    public PromptMessage hostingComparison() {
        return PromptMessage.withUserRole(new TextContent("""
            Hosting Options Comparison

            VPS (Virtual Private Server)
            - Full root access, dedicated resources
            - Best for: web apps, databases, custom software
            - Use listVpsPackages() to see plans
            - Starting from basic (1 vCPU, 1GB RAM) to enterprise (16+ vCPU, 64GB RAM)
            - Payment: monthly, quarterly, semi-annually, annually

            DEDICATED SERVERS
            - Physical hardware, maximum performance
            - Best for: high-traffic sites, game servers, large databases
            - Use getDedicatedServerCatalog() to see configurations
            - Bare-metal performance, no virtualization overhead

            CHOOSING THE RIGHT OPTION
            - Personal blog/portfolio → Smallest VPS plan
            - Small business website → Mid-tier VPS (2-4 vCPU, 4-8GB RAM)
            - E-commerce / SaaS app → High-tier VPS or entry dedicated
            - High-traffic / enterprise → Dedicated server

            COST TIPS
            - Annual VPS billing saves 10-20% vs monthly
            - Start small and upgrade as needed
            - Use previewPaymentFees() to understand total costs
            """));
    }

    @Prompt(name = "troubleshooting")
    public PromptMessage troubleshooting() {
        return PromptMessage.withUserRole(new TextContent("""
            Troubleshooting Guide

            AUTHENTICATION ISSUES
            - "Authentication required" → Call loginWithDevice() to start device authorization
            - "Token expired" → Re-authenticate; tokens expire after ~1 hour
            - Device flow stuck → Start a new flow with loginWithDevice()

            DOMAIN NOT RESOLVING
            1. Check nameservers: getDomainInfo("domain.com")
            2. Verify DNS records: listDnsRecords("domain.com")
            3. Check propagation: DNS changes take up to TTL period (usually 1-3600s)
            4. Verify domain is active (not expired or suspended)

            DOMAIN TRANSFER FAILING
            - Domain must be unlocked: unlockDomain("domain.com")
            - Domain must be 60+ days old
            - Must not expire within 30 days
            - Auth/EPP code must be correct and current
            - Check transfer status: getTransferStatus("domain.com")

            REGISTRATION FAILING
            - Check domain availability first: checkDomainAvailability("domain.com")
            - Ensure sufficient account balance: getAccountBalance()
            - Verify registrant info is complete (name, email, address, phone)
            - Some TLDs have restrictions (residency, trademark, etc.)

            BILLING ISSUES
            - Insufficient balance → createPaymentSession(amount) to add funds
            - Invoice not found → listInvoices() to get correct invoice ID
            - Payment failed → Check balance covers full invoice amount
            """));
    }

    @Prompt(name = "security_best_practices")
    public PromptMessage securityBestPractices() {
        return PromptMessage.withUserRole(new TextContent("""
            Security Best Practices for Domain Management

            ACCOUNT SECURITY
            - Use device flow login (loginWithDevice) for MFA/SSO support
            - Never share your access tokens or EPP/auth codes
            - Review audit logs regularly: getRecentActivity()
            - Monitor for unauthorized changes: getDomainAuditTrail("domain.com")

            DOMAIN SECURITY
            - Enable registrar lock on all domains: lockDomain("domain.com")
            - Enable WHOIS privacy: updateDomainPrivacy("domain.com", true)
            - Enable auto-renewal: updateDomainAutoRenew("domain.com", true)
            - Use strong, unique EPP codes for transfers
            - Only unlock domains when actively transferring

            DNS SECURITY
            - Add SPF record: TXT "v=spf1 include:your-provider ~all"
            - Add DKIM record for email authentication
            - Add DMARC record: TXT "v=DMARC1; p=quarantine; rua=mailto:admin@domain.com"
            - Use low TTL (300) only during changes; revert to 3600 after

            MONITORING
            - Regularly review listUserDomains() for unexpected changes
            - Check getDomainInfo() for status changes on critical domains
            - Set up alerts for domains expiring within 30 days
            - Review getMyAuditLogs() for suspicious activity
            """));
    }
}
