package com.osir.a2a.agents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.osir.a2a.protocol.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrator agent that decomposes complex multi-step tasks
 * into subtasks and delegates them to specialist agents.
 *
 * Handles requests like: "Set up a new client with 3 domains, DNS, and a VPS"
 * by breaking them into ordered steps across Domain, DNS, VPS, Contact, and Billing agents.
 */
@ApplicationScoped
public class OrchestratorAgent implements SpecialistAgent {

    private static final Logger LOG = Logger.getLogger(OrchestratorAgent.class);

    private static final Set<String> ORCHESTRATION_KEYWORDS = Set.of(
            "set up", "setup", "configure everything", "full setup",
            "onboard", "provision everything", "complete setup",
            "multiple", "all of", "end to end", "workflow"
    );

    private static final int STEP_TIMEOUT_SECONDS = 15;

    @Inject AgentRegistry agentRegistry;
    @Inject ObjectMapper objectMapper;

    private AgentCard cachedCard;
    private final ExecutorService stepExecutor = Executors.newFixedThreadPool(4,
            r -> { Thread t = new Thread(r, "orch-step"); t.setDaemon(true); return t; });

    @PreDestroy
    void shutdown() {
        stepExecutor.shutdown();
    }

    @PostConstruct
    void init() { cachedCard = buildAgentCard(); }

    @Override
    public String getId() { return "orchestrator"; }

    @Override
    public AgentCard getAgentCard() { return cachedCard; }

    @Override
    public double score(A2ATask task) {
        Map<String, Object> metadata = task.getMetadata();
        if (metadata != null) {
            String agent = (String) metadata.get("agent");
            if (agent != null) return getId().equals(agent) ? 1.0 : 0.0;
            String skill = (String) metadata.get("skill");
            if ("orchestrate".equals(skill) || "plan_workflow".equals(skill)) return 1.0;
        }

        String text = getLatestUserMessage(task).toLowerCase();
        double score = 0.0;
        for (String kw : ORCHESTRATION_KEYWORDS) {
            if (text.contains(kw)) score += 0.3;
        }
        // Bonus: mentions multiple service areas
        int areas = 0;
        if (text.contains("domain") || text.contains("register")) areas++;
        if (text.contains("dns") || text.contains("record")) areas++;
        if (text.contains("vps") || text.contains("server") || text.contains("hosting")) areas++;
        if (text.contains("contact") || text.contains("registrant")) areas++;
        if (text.contains("billing") || text.contains("invoice") || text.contains("payment")) areas++;
        if (areas >= 2) score += 0.2 * areas;

        return Math.min(score, 1.0);
    }

    @Override
    public A2ATask handle(A2ATask task) {
        try {
            String text = getLatestUserMessage(task);
            List<WorkflowStep> plan = buildPlan(text);

            if (plan.isEmpty()) {
                task.transitionTo(TaskState.INPUT_REQUIRED);
                task.addMessage(new Message("agent",
                        "I can orchestrate complex workflows across domains, DNS, VPS, billing, and contacts. " +
                        "Please describe what you need set up — e.g., 'Register example.com, set up DNS with A record " +
                        "pointing to 1.2.3.4, and order a VPS'."));
                return task;
            }

            // Guard: cap maximum steps to prevent abuse
            int MAX_STEPS = 15;
            if (plan.size() > MAX_STEPS) {
                task.transitionTo(TaskState.FAILED);
                task.addMessage(new Message("agent",
                        "Workflow too complex (" + plan.size() + " steps). Maximum is " + MAX_STEPS +
                        " steps per orchestration. Please break your request into smaller pieces."));
                return task;
            }

            // Present the plan
            StringBuilder planText = new StringBuilder("Workflow plan:\n");
            for (int i = 0; i < plan.size(); i++) {
                WorkflowStep step = plan.get(i);
                planText.append(String.format("%d. [%s] %s\n", i + 1, step.agentId, step.description));
            }

            // Execute each step
            List<Map<String, Object>> results = new ArrayList<>();
            boolean allSuccess = true;

            for (WorkflowStep step : plan) {
                Optional<SpecialistAgent> agent = agentRegistry.findById(step.agentId);
                if (agent.isEmpty()) {
                    results.add(Map.of("step", step.description, "status", "skipped", "reason", "Agent not found: " + step.agentId));
                    continue;
                }

                // Create a sub-task for this step
                A2ATask subTask = new A2ATask(task.getId() + "-step-" + results.size(), new Message("user", step.prompt));
                if (step.skill != null) {
                    subTask.setMetadata(Map.of("skill", step.skill, "agent", step.agentId));
                }
                subTask.transitionTo(TaskState.WORKING);

                // Execute sub-task with per-step timeout
                A2ATask subResult;
                final var agentRef = agent.get();
                try {
                    subResult = stepExecutor.submit(() -> agentRef.handle(subTask))
                            .get(STEP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    subTask.transitionTo(TaskState.FAILED);
                    subTask.addMessage(new Message("agent", "Step timed out after " + STEP_TIMEOUT_SECONDS + "s"));
                    subResult = subTask;
                } catch (ExecutionException e) {
                    subTask.transitionTo(TaskState.FAILED);
                    subTask.addMessage(new Message("agent", "Step execution failed"));
                    subResult = subTask;
                    LOG.warnf("Orchestrator step '%s' error: %s", step.description, e.getCause().getMessage());
                }

                boolean stepSuccess = subResult.getStatus() == TaskState.COMPLETED;
                if (!stepSuccess) allSuccess = false;

                Map<String, Object> stepResult = new LinkedHashMap<>();
                stepResult.put("step", step.description);
                stepResult.put("status", subResult.getStatus().getValue());
                if (!subResult.getArtifacts().isEmpty()) {
                    stepResult.put("data", subResult.getArtifacts().get(0));
                }
                // Get the agent's response message
                subResult.getHistory().stream()
                        .filter(m -> "agent".equals(m.getRole()))
                        .reduce((a, b) -> b) // last agent message
                        .ifPresent(m -> stepResult.put("message", m.getTextContent()));
                results.add(stepResult);

                // If a step requires input, stop and ask
                if (subResult.getStatus() == TaskState.INPUT_REQUIRED) {
                    task.addArtifact(Artifact.ofData("partial-results", Map.of("completed", results)));
                    task.addMessage(new Message("agent",
                            planText + "\nStep " + (results.size()) + " needs more information:\n" +
                            stepResult.get("message")));
                    task.transitionTo(TaskState.INPUT_REQUIRED);
                    return task;
                }

                // If a step failed, continue but note the failure
                if (subResult.getStatus() == TaskState.FAILED) {
                    LOG.warnf("Orchestrator: step '%s' failed: %s", step.description, stepResult.get("message"));
                }
            }

            task.addArtifact(Artifact.ofData("workflow-results", Map.of("steps", results)));
            task.addMessage(new Message("agent",
                    planText + "\nWorkflow " + (allSuccess ? "completed successfully." : "completed with some failures.") +
                    " " + results.size() + " steps executed."));
            task.transitionTo(allSuccess ? TaskState.COMPLETED : TaskState.FAILED);
            return task;

        } catch (Exception e) {
            LOG.errorf(e, "Orchestrator error: %s", e.getMessage());
            task.transitionTo(TaskState.FAILED);
            task.addMessage(new Message("agent", "Orchestration error: " + e.getMessage()));
            return task;
        }
    }

    /**
     * Build a workflow plan by analyzing the user's request.
     * Uses rule-based decomposition for common patterns.
     */
    private List<WorkflowStep> buildPlan(String text) {
        String lower = text.toLowerCase();
        List<WorkflowStep> steps = new ArrayList<>();
        List<String> domains = extractAllDomains(text);

        // Step 1: Contact creation if registrant info mentioned
        if (lower.contains("contact") || lower.contains("registrant") || lower.contains("client")) {
            steps.add(new WorkflowStep("contact-agent", "list_contacts",
                    "List existing contacts", "list contacts"));
        }

        // Step 2: Domain registration
        if (lower.contains("register") || lower.contains("domain")) {
            for (String domain : domains) {
                steps.add(new WorkflowStep("domain-agent", "check_availability",
                        "Check availability of " + domain, "check if " + domain + " is available"));
            }
        }

        // Step 3: DNS setup
        if (lower.contains("dns") || lower.contains("record") || lower.contains("a record")) {
            for (String domain : domains) {
                steps.add(new WorkflowStep("dns-agent", "list_dns_records",
                        "List DNS records for " + domain, "list dns records for " + domain));
            }
        }

        // Step 4: VPS provisioning
        if (lower.contains("vps") || lower.contains("server") || lower.contains("hosting")) {
            steps.add(new WorkflowStep("vps-agent", "list_vps_packages",
                    "List available VPS packages", "list vps packages"));
        }

        // Step 5: Billing check
        if (lower.contains("billing") || lower.contains("balance") || lower.contains("invoice")
                || !domains.isEmpty()) {
            steps.add(new WorkflowStep("billing-agent", "get_balance",
                    "Check account balance", "check account balance"));
        }

        // Step 6: Account overview if requested
        if (lower.contains("account") || lower.contains("overview") || lower.contains("summary")) {
            steps.add(new WorkflowStep("account-agent", "get_account_summary",
                    "Get account summary", "get account summary"));
        }

        return steps;
    }

    private List<String> extractAllDomains(String text) {
        return DomainUtils.extractAll(text);
    }

    private String getLatestUserMessage(A2ATask task) {
        List<Message> h = task.getHistory();
        for (int i = h.size() - 1; i >= 0; i--) {
            if ("user".equals(h.get(i).getRole())) return h.get(i).getTextContent();
        }
        return "";
    }

    private AgentCard buildAgentCard() {
        AgentCard card = new AgentCard();
        card.setName("OSIR Orchestrator");
        card.setDescription("Decomposes complex multi-step tasks and coordinates specialist agents for end-to-end workflows.");
        card.setUrl("/a2a");
        card.setVersion("1.0.0");
        card.setProvider(new AgentCard.AgentProvider("OSIR", "https://osir.com"));
        card.setCapabilities(new AgentCard.AgentCapabilities(false, false));
        card.setAuthentication(new AgentCard.AgentAuthentication(List.of("bearer")));
        card.setSkills(List.of(
                new Skill("orchestrate", "Orchestrate Workflow",
                        "Break down complex tasks into steps and execute across multiple agents"),
                new Skill("plan_workflow", "Plan Workflow",
                        "Create an execution plan for a multi-step task without executing it")
        ));
        return card;
    }

    private record WorkflowStep(String agentId, String skill, String description, String prompt) {}
}
