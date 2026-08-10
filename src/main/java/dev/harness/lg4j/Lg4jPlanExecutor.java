package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.GraphStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
class Lg4jPlanExecutor {

    private static final String DEPENDENCY_FAILED = "dependency failed";
    private static final String BUDGET_EXHAUSTED = "budget exhausted";

    private final Lg4jToolExecutor toolExecutor;
    private final int maxConcurrency;
    private final Lg4jPlanGraphBuilder graphBuilder = new Lg4jPlanGraphBuilder();

    Lg4jPlanExecutor(
            Lg4jToolExecutor toolExecutor,
            @Value("${harness.execution.max-concurrency:5}") int maxConcurrency) {
        this.toolExecutor = toolExecutor;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    Lg4jPlanExecutionState execute(Plan plan, Budget budget) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (budget == null) {
            throw new IllegalArgumentException("budget must not be null");
        }
        try {
            var semaphore = new Semaphore(maxConcurrency);
            return graphBuilder
                    .build(plan, node -> node_async(state -> executeNode(budget, semaphore, node, state)))
                    .compile()
                    .invoke(initialState(budget))
                    .orElseThrow(
                            () -> new IllegalStateException("LangGraph4j plan graph returned no final state"));
        } catch (GraphStateException exception) {
            throw new IllegalStateException("failed to build LangGraph4j plan graph", exception);
        }
    }

    private Map<String, Object> executeNode(
            Budget budget,
            Semaphore semaphore,
            PlanNode node,
            Lg4jPlanExecutionState state) {

        var params = new StateParams(state, node.getId(), null, budget);
        if (dependencyFailed(node, state)) {
            return stateSkipped(params, DEPENDENCY_FAILED);
        }
        if (!budget.tryChargeToolCall()) {
            return stateSkipped(params, BUDGET_EXHAUSTED);
        }

        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            var result = toolExecutor.execute(node.getTool(), materializeArguments(node, state));
            budget.chargeUsage(result.usage());
            return stateUpdate(new StateParams(state, node.getId(), result.value(), budget), NodeStatus.DONE, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return stateFailed(params, exception.getMessage());
        } catch (Exception exception) {
            return stateFailed(params, exception.getMessage());
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private boolean dependencyFailed(PlanNode node, Lg4jPlanExecutionState state) {
        for (var dependencyId : node.getDeps()) {
            var status = state.statuses().get(dependencyId);
            if (status == NodeStatus.FAILED || status == NodeStatus.SKIPPED) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> materializeArguments(PlanNode node, Lg4jPlanExecutionState state) {
        var args = new HashMap<String, Object>();
        for (var binding : node.getArguments()) {
            if (binding == null || binding.argumentName() == null || binding.value() == null) {
                continue;
            }
            var value = materializeValue(binding.value(), state);
            if (value != null) {
                args.put(binding.argumentName(), value);
            }
        }
        return args;
    }

    private Object materializeValue(ArgumentValue value, Lg4jPlanExecutionState state) {
        if (value.type() == ArgumentValueType.LITERAL) {
            return value.literalValue();
        }
        return state.result(value.sourceNodeId());
    }

    private record StateParams(
            Lg4jPlanExecutionState state,
            String nodeId,
            Object result,
            Budget budget) {
    }

    private Map<String, Object> stateSkipped(StateParams params, String error) {
        return stateUpdate(params, NodeStatus.SKIPPED, error);
    }

    private Map<String, Object> stateFailed(StateParams params, String error) {
        return stateUpdate(params, NodeStatus.FAILED, error);
    }

    private Map<String, Object> stateUpdate(
            StateParams params,
            NodeStatus status,
            String error) {
        var result = params.result();
        var nodeId = params.nodeId();

        return Map.of(
                Lg4jPlanExecutionState.RESULTS, result == null ? Map.of() : Map.of(nodeId, result),
                Lg4jPlanExecutionState.STATUSES, Map.of(nodeId, status),
                Lg4jPlanExecutionState.ERRORS, error == null || error.isBlank() ? Map.of() : Map.of(nodeId, error),
                Lg4jPlanExecutionState.BUDGET, params.budget().snapshot());
    }

    private Map<String, Object> initialState(Budget budget) {
        return Map.of(
                Lg4jPlanExecutionState.RESULTS, Map.of(),
                Lg4jPlanExecutionState.STATUSES, Map.of(),
                Lg4jPlanExecutionState.ERRORS, Map.of(),
                Lg4jPlanExecutionState.BUDGET, budget.snapshot());
    }

}
