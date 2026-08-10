package dev.harness.lg4j;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

final class Lg4jPlanExecutionState extends AgentState {

    static final String RESULTS = "results";
    static final String STATUSES = "statuses";
    static final String ERRORS = "errors";
    static final String BUDGET = "budget";

    static final Map<String, Channel<?>> SCHEMA = Map.of();

    Lg4jPlanExecutionState(Map<String, Object> initData) {
        super(initData);
    }

    Map<String, Object> results() {
        return this.<Map<String, Object>>value(RESULTS).orElse(Map.of());
    }

    Map<String, NodeStatus> statuses() {
        return this.<Map<String, NodeStatus>>value(STATUSES).orElse(Map.of());
    }

    Map<String, String> errors() {
        return this.<Map<String, String>>value(ERRORS).orElse(Map.of());
    }

    Optional<BudgetSnapshot> budget() {
        return value(BUDGET);
    }

    Object result(String nodeId) {
        return results().get(nodeId);
    }

    boolean successful(Plan plan) {
        return plan != null && plan.nodes().stream()
                .allMatch(node -> node != null && statuses().get(node.getId()) == NodeStatus.DONE);
    }
}
