package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

@Component
class Lg4jPlanExecutionNode {

    private static final Logger log = LoggerFactory.getLogger(Lg4jPlanExecutionNode.class);

    private final Lg4jPlanExecutor planExecutor;
    private final BudgetLimits budgetLimits;
    private final ModelPricing modelPricing;

    Lg4jPlanExecutionNode(
            Lg4jPlanExecutor planExecutor,
            @Value("${harness.budget.max-tokens:20000}") long maxTokens,
            @Value("${harness.budget.max-tool-calls:50}") long maxToolCalls,
            @Value("${harness.budget.max-wall-clock:60s}") Duration maxWallClock,
            @Value("${harness.budget.max-estimated-cost-usd:0.25}") BigDecimal maxEstimatedCostUsd,
            @Value("${harness.pricing.model:gpt-4.1-mini}") String model,
            @Value("${harness.pricing.input-token-usd:0.0000004}") BigDecimal inputTokenUsd,
            @Value("${harness.pricing.output-token-usd:0.0000016}") BigDecimal outputTokenUsd) {
        this.planExecutor = planExecutor;
        this.budgetLimits = new BudgetLimits(maxTokens, maxToolCalls, maxWallClock, maxEstimatedCostUsd);
        this.modelPricing = new ModelPricing(model, inputTokenUsd, outputTokenUsd);
    }

    Map<String, Object> execute(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }

        var plan = state.plan().orElse(null);
        if (plan == null) {
            return failure("plan must not be null");
        }
        var shape = state.planShape().orElse(null);
        if (shape == null) {
            return failure("plan shape must not be null");
        }

        var budget = new Budget(budgetLimits, modelPricing);
        try {
            var executionState = planExecutor.execute(plan, shape, budget);
            applyExecutionState(plan, executionState);

            if (budget.exhausted()) {
                return Map.of(
                        Lg4jRunState.PLAN, plan,
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "budget exhausted after execution");
            }
            if (hasFailedOrSkippedNode(plan)) {
                return Map.of(
                        Lg4jRunState.PLAN, plan,
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "execution failed");
            }

            return Map.of(
                    Lg4jRunState.PLAN, plan,
                    Lg4jRunState.BUDGET, budget.snapshot());
        } catch (Exception exception) {
            log.warn("LangGraph4j plan execution failed: plan={} error={}",
                    Lg4jDebugValue.dump(plan), Lg4jDebugValue.dump(exception), exception);
            return Map.of(
                    Lg4jRunState.BUDGET, budget.snapshot(),
                    Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                    Lg4jRunState.ERROR, Lg4jDebugValue.dump(exception));
        }
    }

    private void applyExecutionState(Plan plan, Lg4jPlanExecutionState executionState) {
        for (var node : plan.nodes()) {
            var nodeId = node.getId();
            node.setStatus(executionState.statuses().getOrDefault(nodeId, node.getStatus()));
            node.setResult(executionState.result(nodeId));
            node.setError(executionState.errors().get(nodeId));
        }
    }

    private boolean hasFailedOrSkippedNode(Plan plan) {
        return plan.nodes().stream().anyMatch(node -> node != null && (node.isFailed() || node.isSkipped()));
    }

    private Map<String, Object> failure(String error) {
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                Lg4jRunState.ERROR, error);
    }
}
