package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import com.osir.mcp.models.billing.AccountBalanceResult;
import com.osir.mcp.models.billing.InvoiceListResult;
import com.osir.mcp.models.billing.InvoiceStatisticsResult;
import com.osir.mcp.models.billing.DomainPricingResult;
import com.osir.mcp.services.BillingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingSpecialistAgentTest {

    @Mock BillingService billingService;
    @Spy ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks BillingSpecialistAgent agent;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agent.init();
    }

    @Test
    void score_billingKeywords() {
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "check my billing balance"))) > 0.4);
        assertTrue(agent.score(new A2ATask("t1", new Message("user", "show billing invoices"))) > 0.4);
    }

    @Test
    void score_noMatch() {
        assertEquals(0.0, agent.score(new A2ATask("t1", new Message("user", "register domain"))));
    }

    @Test
    void handle_getBalance() {
        when(billingService.getAccountBalance()).thenReturn(new AccountBalanceResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "check account balance"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
        verify(billingService).getAccountBalance();
    }

    @Test
    void handle_listInvoices() {
        when(billingService.listInvoices(null, null, null)).thenReturn(new InvoiceListResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "list all invoices"));
        task.setMetadata(Map.of("skill", "list_invoices"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_statistics() {
        when(billingService.getInvoiceStatistics()).thenReturn(new InvoiceStatisticsResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "invoice statistics summary"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_pricing() {
        when(billingService.getDomainPricing("com")).thenReturn(new DomainPricingResult(true, "OK"));

        A2ATask task = new A2ATask("t1", new Message("user", "domain pricing for com"));
        A2ATask out = agent.handle(task);

        assertEquals(TaskState.COMPLETED, out.getStatus());
    }

    @Test
    void handle_payInvoice_asksForInput() {
        A2ATask task = new A2ATask("t1", new Message("user", "pay my invoice"));
        A2ATask out = agent.handle(task);
        assertEquals(TaskState.INPUT_REQUIRED, out.getStatus());
    }

    @Test
    void getAgentCard_cached() {
        assertSame(agent.getAgentCard(), agent.getAgentCard());
        assertEquals("OSIR Billing Agent", agent.getAgentCard().getName());
    }
}
