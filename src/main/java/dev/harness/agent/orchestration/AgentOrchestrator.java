package dev.harness.agent.orchestration;

import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.execution.DagScheduler;
import dev.harness.agent.planning.Planner;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.validation.DagValidator;
import dev.harness.agent.validation.IncidentPolicyValidator;
import dev.harness.agent.verification.ReportVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
public class AgentOrchestrator {

    private final AgentRuntime runtime;

    public AgentOrchestrator(
            Planner planner,
            DagValidator validator,
            IncidentPolicyValidator policyValidator,
            DagScheduler scheduler,
            ReportVerifier verifier,
            ToolCatalog toolCatalog,
            @Value("${harness.budget.max-tokens:20000}") long maxTokens,
            @Value("${harness.budget.max-tool-calls:50}") long maxToolCalls,
            @Value("${harness.budget.max-wall-clock:60s}") Duration maxWallClock,
            @Value("${harness.budget.max-estimated-cost-usd:0.25}") BigDecimal maxEstimatedCostUsd,
            @Value("${harness.pricing.model:gpt-4.1-mini}") String model,
            @Value("${harness.pricing.input-token-usd:0.0000004}") BigDecimal inputTokenUsd,
            @Value("${harness.pricing.output-token-usd:0.0000016}") BigDecimal outputTokenUsd,
            @Value("${harness.replanning.max-replans:1}") int maxReplans) {
        var budgetLimits = new BudgetLimits(maxTokens, maxToolCalls, maxWallClock, maxEstimatedCostUsd);
        var modelPricing = new ModelPricing(model, inputTokenUsd, outputTokenUsd);
        var lifecycle = new RunLifecycle(toolCatalog, budgetLimits, modelPricing);
        var safeMaxReplans = Math.max(0, maxReplans);
        this.runtime = new AgentRuntime(
                new PlanningLoop(planner, validator, policyValidator, toolCatalog, lifecycle, safeMaxReplans),
                new ExecutionEngine(scheduler, lifecycle, safeMaxReplans),
                lifecycle,
                verifier);
    }

    public RunResult run(RunRequest request) {
        return runtime.run(request);
    }
}
