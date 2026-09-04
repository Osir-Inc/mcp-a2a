package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
import com.osir.a2a.security.ConfirmationGate;
import com.osir.mcp.security.DestructiveOpRateLimiter.Bucket;
import com.osir.mcp.services.BillingService;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class BillingSpecialistAgent extends BaseSpecialistAgent {

    private static final Logger LOG = Logger.getLogger(BillingSpecialistAgent.class);

    @Inject BillingService billingService;

    @Inject
    @org.eclipse.microprofile.rest.client.inject.RestClient
    com.osir.mcp.clients.CatalogBackendClient catalogBackendClient;

    private AgentCard cachedCard;

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "billing-agent"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    protected Set<String> getSkillIds() {
        return Set.of("get_balance", "list_invoices", "get_invoice", "pay_invoice",
                "invoice_statistics", "create_payment", "get_transactions", "preview_fees",
                "get_domain_pricing", "get_hosting_bundle", ConfirmationGate.CONFIRM_SKILL);
    }

    @Override
    protected Set<String> getKeywords() {
        return Set.of("balance", "invoice", "billing", "payment", "pay", "pricing",
                "price", "cost", "fee", "transaction", "checkout", "charge");
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String skill = getSkillFromMetadata(task);
            String text = getLatestUserMessage(task);
            String lower = text.toLowerCase();

            // Before any text matching: an actionId means "run what you staged", not "stage again".
            if (isConfirming(task)) {
                return runConfirmed(task);
            }

            if ("get_hosting_bundle".equals(skill)) {
                return handleHostingBundle(task, text);
            } else if ("get_balance".equals(skill) || lower.contains("balance")) {
                var result = billingService.getAccountBalance();
                return completeWithResult(task, "balance", result, result.isSuccess(),
                        result.isSuccess() ? "Account balance retrieved." : result.getMessage());
            } else if ("list_invoices".equals(skill) || (lower.contains("invoice") && (lower.contains("list") || lower.contains("show") || lower.contains("all")))) {
                var result = billingService.listInvoices(null, null, null);
                return completeWithResult(task, "invoices", result, result.isSuccess(),
                        result.isSuccess() ? "Invoices retrieved." : result.getMessage());
            } else if ("invoice_statistics".equals(skill) || lower.contains("statistic") || lower.contains("summary")) {
                var result = billingService.getInvoiceStatistics();
                return completeWithResult(task, "invoice-stats", result, result.isSuccess(),
                        result.isSuccess() ? "Invoice statistics retrieved." : result.getMessage());
            } else if ("get_transactions".equals(skill) || lower.contains("transaction") || lower.contains("history")) {
                var result = billingService.getPaymentTransactions(null, null);
                return completeWithResult(task, "transactions", result, result.isSuccess(),
                        result.isSuccess() ? "Payment history retrieved." : result.getMessage());
            } else if ("get_domain_pricing".equals(skill) || lower.contains("pricing") || lower.contains("price") || lower.contains("cost")) {
                String extension = null;
                for (String tld : List.of("com", "net", "org", "io", "al", "dev", "tech", "app")) {
                    if (lower.contains("." + tld) || lower.contains(" " + tld + " ") || lower.endsWith(" " + tld)) {
                        extension = tld;
                        break;
                    }
                }
                var result = billingService.getDomainPricing(extension);
                return completeWithResult(task, "pricing", result, result.isSuccess(),
                        result.isSuccess() ? "Domain pricing retrieved." : result.getMessage());
            } else if ("get_invoice".equals(skill)) {
                String invoiceId = meta(task, "invoiceId");
                if (invoiceId == null) return askForInput(task, "Please provide the invoice ID to retrieve details for.");
                var result = billingService.getInvoiceDetails(invoiceId);
                return completeWithResult(task, "invoice", result, result.isSuccess(),
                        result.isSuccess() ? "Invoice details retrieved." : result.getMessage());
            } else if ("preview_fees".equals(skill) || lower.contains("fee") || lower.contains("preview")) {
                Double amount = metaDouble(task, "amount");
                if (amount == null) return askForInput(task, "Please provide the payment amount to preview fees for.");
                var result = billingService.previewPaymentFees(amount, meta(task, "currency"));
                return completeWithResult(task, "fee-preview", result, result.isSuccess(),
                        result.isSuccess() ? "Fee preview retrieved." : result.getMessage());
            } else if ("pay_invoice".equals(skill) || lower.contains("pay")) {
                String invoiceId = meta(task, "invoiceId");
                if (invoiceId == null) return askForInput(task, "Please provide the invoice ID to pay.");
                return stage(task, "pay_invoice", java.util.Map.of("invoiceId", invoiceId),
                        "Pay invoice '" + invoiceId + "' from the account balance. This MOVES MONEY and "
                                + "cannot be undone from here.",
                        Bucket.FINANCIAL);
            } else if ("create_payment".equals(skill) || lower.contains("checkout") || lower.contains("add funds")) {
                Double amount = metaDouble(task, "amount");
                if (amount == null) return askForInput(task, "Please provide the amount to add to your account balance.");
                String currency = meta(task, "currency");
                java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
                params.put("amount", amount);
                if (currency != null) params.put("currency", currency);
                return stage(task, "create_payment", params,
                        "Open a checkout session to add " + amount + " " + (currency == null ? "" : currency)
                                + " to the account balance. The card is charged when the person completes "
                                + "checkout at the returned URL.",
                        Bucket.FINANCIAL);
            } else {
                var result = billingService.getAccountBalance();
                return completeWithResult(task, "balance", result, result.isSuccess(),
                        result.isSuccess() ? "Account balance retrieved." : result.getMessage());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Billing agent error: %s", e.getMessage());
            return failWithError(task, e.getMessage());
        }
    }

    /** Run what was staged, from the parameters frozen at stage time. */
    private A2ATask runConfirmed(A2ATask task) {
        var claim = confirmationGate.claim(task);
        if (!claim.ok()) {
            return failWithError(task, claim.error());
        }
        java.util.Map<String, Object> p = claim.action().params();
        switch (claim.action().skill()) {
            case "pay_invoice" -> {
                var result = billingService.payInvoice(String.valueOf(p.get("invoiceId")));
                return completeWithResult(task, "payment", result, result.isSuccess(),
                        result.isSuccess() ? "Invoice paid successfully." : result.getMessage());
            }
            case "create_payment" -> {
                Double amount = p.get("amount") instanceof Number n ? n.doubleValue() : null;
                Object currency = p.get("currency");
                var result = billingService.createPaymentSession(amount,
                        currency == null ? null : currency.toString());
                return completeWithResult(task, "payment-session", result, result.isSuccess(),
                        result.isSuccess() ? "Payment session created. Use the URL to complete checkout."
                                : result.getMessage());
            }
            default -> {
                return failWithError(task, "Cannot run '" + claim.action().skill() + "': unknown staged action.");
            }
        }
    }

    /**
     * Hosting options + exact prices for ONE domain. Anonymous on the backend, so it also answers
     * "what would this cost me" before the caller has an account.
     */
    private A2ATask handleHostingBundle(A2ATask task, String text) {
        String domain = meta(task, "domain");
        if (domain == null) domain = extractDomain(text);
        if (domain == null) {
            return askForInput(task, "Please provide the domain to price hosting for, in metadata: domain.");
        }
        try {
            var bundle = catalogBackendClient.getHostingBundle(domain);
            return completeWithResult(task, "hosting-bundle", bundle, true, "Hosting options retrieved.");
        } catch (Exception e) {
            LOG.errorf(e, "hosting bundle failed for %s", domain);
            return failWithError(task, "Could not get hosting options for " + domain + " right now.");
        }
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Billing Agent");
        card.setDescription("Manages account balance, invoices, payments, and domain pricing.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("get_balance", "Get Account Balance", "Check current account balance",
                        List.of("billing", "balance", "funds"),
                        List.of("What is my account balance?", "How much credit do I have left?")),
                new Skill("list_invoices", "List Invoices", "List all invoices",
                        List.of("billing", "invoices", "list"),
                        List.of("Show me all my invoices", "Do I have any unpaid invoices?")),
                new Skill("get_invoice", "Get Invoice Details", "Get details of a specific invoice",
                        List.of("billing", "invoice", "details"),
                        List.of("Show me invoice INV-2024-0417", "What is on my latest invoice?")),
                new Skill("pay_invoice", "Pay Invoice", "Pay an outstanding invoice from balance",
                        List.of("billing", "payment", "invoice"),
                        List.of("Pay invoice INV-2024-0417 from my balance", "Settle my open invoice")),
                new Skill("invoice_statistics", "Invoice Statistics", "Get invoice summary stats",
                        List.of("billing", "invoices", "statistics"),
                        List.of("How much have I spent on invoices this year?", "Give me a summary of my billing")),
                new Skill("create_payment", "Create Payment Session", "Add funds via Stripe checkout",
                        List.of("billing", "payment", "topup"),
                        List.of("Add 50 EUR to my account", "I want to top up my balance with 100 dollars")),
                new Skill("get_transactions", "Get Transactions", "View payment transaction history",
                        List.of("billing", "transactions", "history"),
                        List.of("Show my payment history", "List my recent transactions")),
                new Skill("preview_fees", "Preview Fees", "Preview fees for a payment amount",
                        List.of("billing", "fees", "preview"),
                        List.of("What fees would I pay on a 75 EUR payment?")),
                new Skill("get_domain_pricing", "Get Domain Pricing", "Get pricing for domain extensions",
                        List.of("billing", "pricing", "domains"),
                        List.of("How much does a .io domain cost?", "What is the price for .com registrations?")),
                new Skill(ConfirmationGate.CONFIRM_SKILL, "Confirm A Staged Action",
                        "Run an action this agent staged: send the actionId it returned, on the same task",
                        List.of("confirmation", "safety"),
                        List.of("Confirm action a2a_1f4c... on this task",
                                "Yes, pay that invoice")),
                new Skill("get_hosting_bundle", "Get Hosting Bundle",
                        "Hosting options and exact prices for one domain: recommended VPS, mail plan and totals",
                        List.of("billing", "pricing", "hosting", "bundle"),
                        List.of("What would it cost to host cedarloop.com with email?",
                                "Give me the hosting options for brahaj.al"))
        ));
        return card;
    }
}
