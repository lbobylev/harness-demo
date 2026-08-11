package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Lg4jPlanExecutorTests {

    @Test
    void doesNotExecuteMoreReadyNodesThanRemainingToolCallBudget() {
        var budget = budget(100, 1);
        var tools = new Lg4jTools();
        var executor = new Lg4jPlanExecutor(
                new Lg4jToolExecutor(tools),
                new Lg4jEvidenceAnalysisNode(tools),
                4);
        var plan = new Plan(List.of(
                new PlanNode("metrics", "query_prometheus", List.of()),
                new PlanNode("logs", "query_loki", List.of())));

        var state = executor.execute(plan, budget);

        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(1L);
        assertThat(state.statuses().entrySet())
                .filteredOn(entry -> List.of("metrics", "logs").contains(entry.getKey()))
                .extracting(Map.Entry::getValue)
                .containsExactlyInAnyOrder(NodeStatus.DONE, NodeStatus.SKIPPED);
        assertThat(state.errors()).containsValue("budget exhausted");
    }

    private static Budget budget(long maxTokens, long maxToolCalls) {
        return new Budget(
                new BudgetLimits(maxTokens, maxToolCalls, Duration.ofMinutes(1), new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
