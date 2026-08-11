package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.execution.AgentResponse;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
class Lg4jPlanExecutor {

    private static final String DEPENDENCY_FAILED = "dependency failed";
    private static final String BUDGET_EXHAUSTED = "budget exhausted";

    private final Lg4jAgentExecutor agentExecutor;
    private final Lg4jEvidenceAnalysisNode evidenceAnalysisNode;
    private final int maxConcurrency;
    private final Lg4jPlanGraphBuilder graphBuilder = new Lg4jPlanGraphBuilder();

    Lg4jPlanExecutor(
            Lg4jAgentExecutor agentExecutor,
            Lg4jEvidenceAnalysisNode evidenceAnalysisNode,
            @Value("${harness.execution.max-concurrency:5}") int maxConcurrency) {
        this.agentExecutor = agentExecutor;
        this.evidenceAnalysisNode = evidenceAnalysisNode;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    Lg4jPlanExecutionState execute(Plan plan, Budget budget) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (budget == null) {
            throw new IllegalArgumentException("budget must not be null");
        }
        ExecutorService graphExecutor = Executors.newVirtualThreadPerTaskExecutor();
            ExecutorService agentInvocationExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var semaphore = new Semaphore(maxConcurrency);
            return graphBuilder
                    .build(plan,
                            node -> node_async(state -> executeNode(budget, semaphore, agentInvocationExecutor, node, state)),
                            node_async(state -> analyzeEvidence(plan, budget, agentInvocationExecutor, state)))
                    .compile()
                    .invoke(initialState(budget), parallelConfig(plan, graphExecutor))
                    .orElseThrow(() -> new IllegalStateException("LangGraph4j plan graph returned no final state"));
        } catch (GraphStateException exception) {
            throw new IllegalStateException(
                    "failed to build LangGraph4j plan graph: plan=%s error=%s"
                            .formatted(Lg4jDebugValue.dump(plan), Lg4jDebugValue.dump(exception)),
                    exception);
        } finally {
            graphExecutor.shutdownNow();
            agentInvocationExecutor.shutdownNow();
        }
    }

    private RunnableConfig parallelConfig(Plan plan, ExecutorService executor) {
        var builder = RunnableConfig.builder()
                .addParallelNodeExecutor(Lg4jPlanGraphBuilder.FORK, executor)
                .addParallelNodeExecutor(START, executor);
        for (var node : plan.nodes()) {
            builder.addParallelNodeExecutor(node.getId(), executor);
        }
        return builder.build();
    }

    private Map<String, Object> executeNode(
            Budget budget,
            Semaphore semaphore,
            ExecutorService agentInvocationExecutor,
            PlanNode node,
            Lg4jPlanExecutionState state) {

        var params = new StateParams(state, node.getId(), null, budget);
        if (dependencyFailed(node, state)) {
            return stateSkipped(params, DEPENDENCY_FAILED);
        }
        if (budget.wallClockExhausted()) {
            return stateSkipped(params, BUDGET_EXHAUSTED);
        }
        if (!budget.tryChargeAgentInvocation()) {
            return stateSkipped(params, BUDGET_EXHAUSTED);
        }

        boolean acquired = false;
        try {
            var remaining = budget.remainingWallClock();
            if (remaining.isZero() || !semaphore.tryAcquire(remaining.toNanos(), TimeUnit.NANOSECONDS)) {
                return stateSkipped(params, BUDGET_EXHAUSTED);
            }
            acquired = true;
            var result = executeAgentInvocation(agentInvocationExecutor, budget, node, state);
            budget.chargeUsage(result.spent().aiUsage());
            return stateUpdate(new StateParams(state, node.getId(), result.value(), budget), NodeStatus.DONE, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return budget.wallClockExhausted()
                    ? stateSkipped(params, BUDGET_EXHAUSTED)
                    : stateFailed(params, errorMessage(exception));
        } catch (TimeoutException exception) {
            return stateSkipped(params, BUDGET_EXHAUSTED);
        } catch (ExecutionException exception) {
            return stateFailed(params, errorMessage(exception.getCause()));
        } catch (Exception exception) {
            return stateFailed(params, errorMessage(exception));
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private AgentResponse executeAgentInvocation(
            ExecutorService agentInvocationExecutor,
            Budget budget,
            PlanNode node,
            Lg4jPlanExecutionState state) throws InterruptedException, ExecutionException, TimeoutException {
        var args = materializeArguments(node, state);
        Future<AgentResponse> future = agentInvocationExecutor.submit(() -> agentExecutor.execute(node.getAgent(), args));
        try {
            return future.get(timeoutNanos(budget), TimeUnit.NANOSECONDS);
        } catch (InterruptedException | TimeoutException exception) {
            future.cancel(true);
            throw exception;
        }
    }

    private Map<String, Object> analyzeEvidence(
            Plan plan,
            Budget budget,
            ExecutorService agentInvocationExecutor,
            Lg4jPlanExecutionState state) {
        var params = new StateParams(state, Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, null, budget);
        if (budget.wallClockExhausted()) {
            return stateSkipped(params, BUDGET_EXHAUSTED);
        }
        Future<Map<String, Object>> future = agentInvocationExecutor.submit(() -> evidenceAnalysisNode.analyze(plan, state));
        try {
            return future.get(timeoutNanos(budget), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return budget.wallClockExhausted()
                    ? stateSkipped(params, BUDGET_EXHAUSTED)
                    : stateFailed(params, errorMessage(exception));
        } catch (TimeoutException exception) {
            future.cancel(true);
            return stateSkipped(params, BUDGET_EXHAUSTED);
        } catch (ExecutionException exception) {
            return stateFailed(params, errorMessage(exception.getCause()));
        }
    }

    private long timeoutNanos(Budget budget) throws TimeoutException {
        var remaining = budget.remainingWallClock();
        if (remaining.isZero()) {
            throw new TimeoutException(BUDGET_EXHAUSTED);
        }
        return remaining.toNanos();
    }

    private String errorMessage(Throwable exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return "execution failed";
        }
        return exception.getMessage();
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
        var budget = params.budget();

        var update = new HashMap<String, Object>();
        update.put(Lg4jPlanExecutionState.RESULTS, result == null ? Map.of() : Map.of(nodeId, result));
        update.put(Lg4jPlanExecutionState.STATUSES, Map.of(nodeId, status));
        update.put(Lg4jPlanExecutionState.ERRORS, error == null || error.isBlank() ? Map.of() : Map.of(nodeId, error));
        if (budget != null) {
            update.put(Lg4jPlanExecutionState.BUDGET, budget.snapshot());
        } else {
            params.state().budget().ifPresent(snapshot -> update.put(Lg4jPlanExecutionState.BUDGET, snapshot));
        }
        return update;
    }

    private Map<String, Object> initialState(Budget budget) {
        return Map.of(
                Lg4jPlanExecutionState.RESULTS, Map.of(),
                Lg4jPlanExecutionState.STATUSES, Map.of(),
                Lg4jPlanExecutionState.ERRORS, Map.of(),
                Lg4jPlanExecutionState.BUDGET, budget.snapshot());
    }
}
