package dev.harness.lg4j;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class Lg4jPlanNode {

    private final Lg4jPlanner planner;

    Lg4jPlanNode(Lg4jPlanner planner) {
        this.planner = planner;
    }

    Map<String, Object> plan(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }
        try {
            var result = planner.plan(state.goal().orElse(""), state.failureContext().orElse(null));
            return Map.of(Lg4jRunState.PLAN, result.plan());
        } catch (Exception exception) {
            return Map.of(
                    Lg4jRunState.STATUS, RunStatus.FAILED_PLANNING,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                    Lg4jRunState.ERROR, exception.getMessage() == null ? "planning failed" : exception.getMessage()
            );
        }
    }
}
