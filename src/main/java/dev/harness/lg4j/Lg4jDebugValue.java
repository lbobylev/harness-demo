package dev.harness.lg4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;

import java.util.stream.Collectors;

final class Lg4jDebugValue {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    private Lg4jDebugValue() {
    }

    static String dump(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Throwable throwable) {
            return dumpThrowable(throwable);
        }
        if (value instanceof Plan plan) {
            return dumpPlan(plan);
        }
        if (value instanceof PlanNode node) {
            return dumpNode(node);
        }
        if (value instanceof Lg4jRunState state) {
            return dumpRunState(state);
        }
        if (value instanceof Lg4jPlanExecutionState state) {
            return dumpExecutionState(state);
        }
        if (value instanceof BudgetSnapshot budget) {
            return dumpBudget(budget);
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private static String dumpThrowable(Throwable throwable) {
        var parts = new java.util.ArrayList<String>();
        var current = throwable;
        while (current != null) {
            var message = current.getMessage();
            parts.add(current.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message));
            current = current.getCause();
        }
        return String.join(" caused by ", parts);
    }

    private static String dumpPlan(Plan plan) {
        return "Plan[nodes=%s]".formatted(plan.nodes().stream()
                .map(Lg4jDebugValue::dumpNode)
                .collect(Collectors.joining(", ")));
    }

    private static String dumpNode(PlanNode node) {
        return "%s(agent=%s,deps=%s,status=%s,error=%s)"
                .formatted(node.getId(), node.getAgent(), node.getDeps(), node.getStatus(), node.getError());
    }

    private static String dumpRunState(Lg4jRunState state) {
        return "Lg4jRunState(runId=%s,status=%s,error=%s,plan=%s,budget=%s)"
                .formatted(
                        state.runId().orElse(null),
                        state.status().orElse(null),
                        state.error().orElse(null),
                        state.plan().map(Lg4jDebugValue::dumpPlan).orElse(null),
                        state.budget().map(Lg4jDebugValue::dumpBudget).orElse(null));
    }

    private static String dumpExecutionState(Lg4jPlanExecutionState state) {
        return "Lg4jPlanExecutionState(statuses=%s,errors=%s,budget=%s)"
                .formatted(
                        state.statuses(),
                        state.errors(),
                        state.budget().map(Lg4jDebugValue::dumpBudget).orElse(null));
    }

    private static String dumpBudget(BudgetSnapshot budget) {
        return "Budget(tokens=%d/%d,agents=%d/%d,cost=%s/%s,pressure=%.3f)"
                .formatted(
                        budget.tokensUsed(),
                        budget.maxTokens(),
                        budget.agentInvocationsUsed(),
                        budget.maxAgentInvocations(),
                        budget.estimatedCostUsd(),
                        budget.maxEstimatedCostUsd(),
                        budget.pressure());
    }
}
