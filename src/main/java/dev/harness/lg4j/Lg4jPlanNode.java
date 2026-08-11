package dev.harness.lg4j;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.budget.Budget;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class Lg4jPlanNode {

    private final Lg4jPlanner planner;

    Lg4jPlanNode(Lg4jPlanner planner) {
        this.planner = planner;
    }

    Map<String, Object> plan(Lg4jRunState state, Budget budget) {
        if (state.terminal()) {
            return Map.of();
        }
        if (budget == null) {
            return failure("budget must be present");
        }

        try {
            var result = planner.plan(state.goal().orElse(""), state.failureContext().orElse(null));
            budget.chargeUsage(result.usage());
            if (budget.exhausted()) {
                return Map.of(
                        Lg4jRunState.PLAN, result.plan(),
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "budget exhausted after planning"
                );
            }
            return Map.of(
                    Lg4jRunState.PLAN, result.plan(),
                    Lg4jRunState.BUDGET, budget.snapshot());
        } catch (Exception exception) {
            return failure(exception.getMessage() == null ? "planning failed" : exception.getMessage());
        }
    }

    private Map<String, Object> failure(String error) {
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_PLANNING,
                Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                Lg4jRunState.ERROR, error
        );
    }
}
