package dev.harness.agent.execution;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.PlanNode;

import java.time.Duration;

@FunctionalInterface
public interface NodeExecutionListener {

    void onNodeEvent(PlanNode node, String kind, Duration latency, Budget budget);

    static NodeExecutionListener noop() {
        return (node, kind, latency, budget) -> {
        };
    }
}
