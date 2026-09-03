package com.osir.a2a.agents;

import com.osir.a2a.protocol.*;
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
                "invoice_statistics", "create_payment", "get_transactions", "preview_fees", "get_domain_pricing");
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

            if ("get_balance".equals(skill) || lower.contains("balance")) {
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
                var result = billingService.payInvoice(invoiceId);
                return completeWithResult(task, "payment", result, result.isSuccess(),
                        result.isSuccess() ? "Invoice paid successfully." : result.getMessage());
            } else if ("create_payment".equals(skill) || lower.contains("checkout") || lower.contains("add funds")) {
                Double amount = metaDouble(task, "amount");
                if (amount == null) return askForInput(task, "Please provide the amount to add to your account balance.");
                var result = billingService.createPaymentSession(amount, meta(task, "currency"));
                return completeWithResult(task, "payment-session", result, result.isSuccess(),
                        result.isSuccess() ? "Payment session created. Use the URL to complete checkout." : result.getMessage());
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
                        List.of("How much does a .io domain cost?", "What is the price for .com registrations?"))
        ));
        return card;
    }
}
