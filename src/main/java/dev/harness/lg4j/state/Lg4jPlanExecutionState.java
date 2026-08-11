package dev.harness.lg4j.state;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class Lg4jPlanExecutionState extends AgentState {

    public static final String RESULTS = "results";
    public static final String STATUSES = "statuses";
    public static final String ERRORS = "errors";
    public static final String BUDGET = "budget";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            RESULTS, Channels.<Map<String, Object>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new),
            STATUSES,
            Channels.<Map<String, NodeStatus>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new),
            ERRORS, Channels.<Map<String, String>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new));

    public Lg4jPlanExecutionState(Map<String, Object> initData) {
        super(initData);
    }

    public Map<String, Object> results() {
        return this.<Map<String, Object>>value(RESULTS).orElse(Map.of());
    }

    public Map<String, NodeStatus> statuses() {
        return this.<Map<String, NodeStatus>>value(STATUSES).orElse(Map.of());
    }

    public Map<String, String> errors() {
        return this.<Map<String, String>>value(ERRORS).orElse(Map.of());
    }

    public Optional<BudgetSnapshot> budget() {
        return value(BUDGET);
    }

    public Object result(String nodeId) {
        return results().get(nodeId);
    }

    public boolean successful(Plan plan) {
        return plan != null && plan.nodes().stream()
                .allMatch(node -> node != null && statuses().get(node.getId()) == NodeStatus.DONE);
    }

    private static <T> Map<String, T> merge(Map<String, T> oldValue, Map<String, T> newValue) {
        var merged = new LinkedHashMap<String, T>();
        if (oldValue != null) {
            merged.putAll(oldValue);
        }
        if (newValue != null) {
            merged.putAll(newValue);
        }
        return merged;
    }
}
