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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Lg4jPlanExecutionNodeTests {

    @Test
    void usesRunBudgetFromState() {
        var budget = budget(100, 10);
        var plan = new Plan(List.of(new PlanNode("metrics", "query_prometheus", List.of())));
        var executor = mock(Lg4jPlanExecutor.class);
        when(executor.execute(same(plan), same(budget))).thenReturn(new Lg4jPlanExecutionState(Map.of(
                Lg4jPlanExecutionState.RESULTS, Map.of(),
                Lg4jPlanExecutionState.STATUSES, Map.of("metrics", NodeStatus.DONE),
                Lg4jPlanExecutionState.ERRORS, Map.of(),
                Lg4jPlanExecutionState.BUDGET, budget.snapshot())));

        new Lg4jPlanExecutionNode(executor).execute(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN, plan,
                Lg4jRunState.BUDGET, budget.snapshot())), budget);

        verify(executor).execute(same(plan), same(budget));
    }

    private static Budget budget(long maxTokens, long maxToolCalls) {
        return new Budget(
                new BudgetLimits(maxTokens, maxToolCalls, Duration.ofMinutes(1), new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
