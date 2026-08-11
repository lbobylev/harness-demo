package dev.harness.lg4j;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Lg4jPlanNodeTests {

    @Test
    void chargesPlanningUsageToRunBudget() {
        var budget = budget(100, 10);
        var plan = new Plan(List.of(new PlanNode("metrics", "query_prometheus", List.of())));
        var planner = mock(Lg4jPlanner.class);
        when(planner.plan("investigate", null))
                .thenReturn(new Lg4jPlanningResult(plan, new AiUsage("test-model", 6, 4, 10)));

        var update = new Lg4jPlanNode(planner).plan(runState(budget));

        assertThat(update).containsEntry(Lg4jRunState.PLAN, plan);
        assertThat((BudgetSnapshot) update.get(Lg4jRunState.BUDGET))
                .extracting(BudgetSnapshot::tokensUsed)
                .isEqualTo(10L);
        assertThat(budget.snapshot().tokensUsed()).isEqualTo(10L);
    }

    @Test
    void stopsWhenPlanningExhaustsBudget() {
        var budget = budget(10, 10);
        var plan = new Plan(List.of(new PlanNode("metrics", "query_prometheus", List.of())));
        var planner = mock(Lg4jPlanner.class);
        when(planner.plan("investigate", null))
                .thenReturn(new Lg4jPlanningResult(plan, new AiUsage("test-model", 8, 4, 12)));

        var update = new Lg4jPlanNode(planner).plan(runState(budget));

        assertThat(update)
                .containsEntry(Lg4jRunState.PLAN, plan)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED)
                .containsEntry(Lg4jRunState.ERROR, "budget exhausted after planning");
        assertThat((BudgetSnapshot) update.get(Lg4jRunState.BUDGET))
                .extracting(BudgetSnapshot::tokensUsed)
                .isEqualTo(12L);
    }

    private static Lg4jRunState runState(Budget budget) {
        return new Lg4jRunState(Map.of(
                Lg4jRunState.GOAL, "investigate",
                Lg4jRunState.BUDGET_RUNTIME, budget,
                Lg4jRunState.BUDGET, budget.snapshot()));
    }

    private static Budget budget(long maxTokens, long maxToolCalls) {
        return new Budget(
                new BudgetLimits(maxTokens, maxToolCalls, Duration.ofMinutes(1), new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
