package dev.harness.lg4j.execution;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.execution.AgentResponse;
import dev.harness.lg4j.agents.Lg4jAgentExecutor;
import dev.harness.lg4j.graph.Lg4jDebugValue;
import dev.harness.lg4j.graph.Lg4jPlanDag;
import dev.harness.lg4j.nodes.Lg4jEvidenceAnalysisNode;
import dev.harness.lg4j.state.Lg4jPlanExecutionState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class Lg4jPlanExecutor {

    private static final String DEPENDENCY_FAILED = "dependency failed";
    private static final String BUDGET_EXHAUSTED = "budget exhausted";

    private final Lg4jAgentExecutor agentExecutor;
    private final Lg4jEvidenceAnalysisNode evidenceAnalysisNode;
    private final int maxConcurrency;

    public Lg4jPlanExecutor(
            Lg4jAgentExecutor agentExecutor,
            Lg4jEvidenceAnalysisNode evidenceAnalysisNode,
            @Value("${harness.execution.max-concurrency:5}") int maxConcurrency) {
        this.agentExecutor = agentExecutor;
        this.evidenceAnalysisNode = evidenceAnalysisNode;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public Lg4jPlanExecutionState execute(Plan plan, Budget budget) {
        if (plan == null) {
            throw new IllegalArgumentException("plan must not be null");
        }
        if (budget == null) {
            throw new IllegalArgumentException("budget must not be null");
        }
        ExecutorService agentInvocationExecutor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            var execution = new Execution(initialState(budget));
            executePlanNodes(plan, budget, agentInvocationExecutor, execution);
            execution.apply(analyzeEvidence(plan, budget, agentInvocationExecutor, execution.state()));
            return execution.state();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "failed to execute plan: plan=%s error=%s"
                            .formatted(Lg4jDebugValue.dump(plan), Lg4jDebugValue.dump(exception)),
                    exception);
        } finally {
            agentInvocationExecutor.shutdownNow();
        }
    }

    private void executePlanNodes(
            Plan plan,
            Budget budget,
            ExecutorService agentInvocationExecutor,
            Execution execution) {
        var nodesById = Lg4jPlanDag.nodesById(plan);
        var dependents = dependents(plan);
        var remainingDeps = remainingDeps(plan);
        var ready = readyNodes(plan, remainingDeps);
        ExecutorService nodeExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CompletionService<NodeUpdate> completions = new ExecutorCompletionService<>(nodeExecutor);
        var running = 0;

        try {
            while (!ready.isEmpty() || running > 0) {
                while (running < maxConcurrency && !ready.isEmpty()) {
                    var node = ready.remove();
                    var state = execution.state();
                    completions.submit(() -> new NodeUpdate(
                            node.getId(), executeNode(budget, agentInvocationExecutor, node, state)));
                    running++;
                }

                var completed = take(completions);
                running--;
                execution.apply(completed.update());
                for (var dependentId : dependents.getOrDefault(completed.nodeId(), List.of())) {
                    if (remainingDeps.merge(dependentId, -1, Integer::sum) == 0) {
                        ready.add(nodesById.get(dependentId));
                    }
                }
            }
        } finally {
            nodeExecutor.shutdownNow();
        }
    }

    private NodeUpdate take(CompletionService<NodeUpdate> completions) {
        try {
            return completions.take().get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorMessage(exception), exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(errorMessage(exception.getCause()), exception.getCause());
        }
    }

    private Map<String, List<String>> dependents(Plan plan) {
        var dependents = new HashMap<String, List<String>>();
        for (var node : plan.nodes()) {
            for (var dep : node.getDeps()) {
                dependents.computeIfAbsent(dep, ignored -> new ArrayList<>()).add(node.getId());
            }
        }
        return dependents;
    }

    private Map<String, Integer> remainingDeps(Plan plan) {
        var remainingDeps = new HashMap<String, Integer>();
        for (var node : plan.nodes()) {
            remainingDeps.put(node.getId(), node.getDeps().size());
        }
        return remainingDeps;
    }

    private Queue<PlanNode> readyNodes(Plan plan, Map<String, Integer> remainingDeps) {
        var ready = new ArrayDeque<PlanNode>();
        for (var node : plan.nodes()) {
            if (remainingDeps.get(node.getId()) == 0) {
                ready.add(node);
            }
        }
        return ready;
    }

    private Map<String, Object> executeNode(
            Budget budget,
            ExecutorService agentInvocationExecutor,
            PlanNode node,
            Lg4jPlanExecutionState state) {

        if (dependencyFailed(node, state)) {
            return stateUpdate(state, node.getId(), null, budget, NodeStatus.SKIPPED, DEPENDENCY_FAILED);
        }
        if (budget.wallClockExhausted() || !budget.tryChargeAgentInvocation()) {
            return stateUpdate(state, node.getId(), null, budget, NodeStatus.SKIPPED, BUDGET_EXHAUSTED);
        }
        try {
            var result = executeAgentInvocation(agentInvocationExecutor, budget, node, state);
            budget.chargeUsage(result.spent().aiUsage());
            return stateUpdate(state, node.getId(), result.value(), budget, NodeStatus.DONE, null);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return budget.wallClockExhausted()
                    ? stateUpdate(state, node.getId(), null, budget, NodeStatus.SKIPPED, BUDGET_EXHAUSTED)
                    : stateUpdate(state, node.getId(), null, budget, NodeStatus.FAILED, errorMessage(exception));
        } catch (TimeoutException exception) {
            return stateUpdate(state, node.getId(), null, budget, NodeStatus.SKIPPED, BUDGET_EXHAUSTED);
        } catch (ExecutionException exception) {
            return stateUpdate(state, node.getId(), null, budget, NodeStatus.FAILED,
                    errorMessage(exception.getCause()));
        } catch (Exception exception) {
            return stateUpdate(state, node.getId(), null, budget, NodeStatus.FAILED, errorMessage(exception));
        }
    }

    private AgentResponse executeAgentInvocation(
            ExecutorService agentInvocationExecutor,
            Budget budget,
            PlanNode node,
            Lg4jPlanExecutionState state) throws InterruptedException, ExecutionException, TimeoutException {
        var args = materializeArguments(node, state);
        Future<AgentResponse> future = agentInvocationExecutor
                .submit(() -> agentExecutor.execute(node.getAgent(), args));
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
        if (budget.wallClockExhausted()) {
            return stateUpdate(state, Lg4jPlanExecutionState.ANALYZE_EVIDENCE, null, budget, NodeStatus.SKIPPED,
                    BUDGET_EXHAUSTED);
        }
        Future<Map<String, Object>> future = agentInvocationExecutor
                .submit(() -> evidenceAnalysisNode.analyze(plan, state));
        try {
            return future.get(timeoutNanos(budget), TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return budget.wallClockExhausted()
                    ? stateUpdate(state, Lg4jPlanExecutionState.ANALYZE_EVIDENCE, null, budget, NodeStatus.SKIPPED,
                            BUDGET_EXHAUSTED)
                    : stateUpdate(state, Lg4jPlanExecutionState.ANALYZE_EVIDENCE, null, budget, NodeStatus.FAILED,
                            errorMessage(exception));
        } catch (TimeoutException exception) {
            future.cancel(true);
            return stateUpdate(state, Lg4jPlanExecutionState.ANALYZE_EVIDENCE, null, budget, NodeStatus.SKIPPED,
                    BUDGET_EXHAUSTED);
        } catch (ExecutionException exception) {
            return stateUpdate(state, Lg4jPlanExecutionState.ANALYZE_EVIDENCE, null, budget, NodeStatus.FAILED,
                    errorMessage(exception.getCause()));
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

    private Map<String, Object> stateUpdate(
            Lg4jPlanExecutionState state,
            String nodeId,
            Object result,
            Budget budget,
            NodeStatus status,
            String error) {

        var update = new HashMap<String, Object>();
        update.put(Lg4jPlanExecutionState.RESULTS, result == null ? Map.of() : Map.of(nodeId, result));
        update.put(Lg4jPlanExecutionState.STATUSES, Map.of(nodeId, status));
        update.put(Lg4jPlanExecutionState.ERRORS, error == null || error.isBlank() ? Map.of() : Map.of(nodeId, error));
        if (budget != null) {
            update.put(Lg4jPlanExecutionState.BUDGET, budget.snapshot());
        } else {
            state.budget().ifPresent(snapshot -> update.put(Lg4jPlanExecutionState.BUDGET, snapshot));
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

    private record NodeUpdate(String nodeId, Map<String, Object> update) {
    }

    private static final class Execution {

        private final Map<String, Object> results = new LinkedHashMap<>();
        private final Map<String, NodeStatus> statuses = new LinkedHashMap<>();
        private final Map<String, String> errors = new LinkedHashMap<>();
        private Object budget;

        private Execution(Map<String, Object> initialState) {
            apply(initialState);
        }

        private Lg4jPlanExecutionState state() {
            var data = new HashMap<String, Object>();
            data.put(Lg4jPlanExecutionState.RESULTS, new LinkedHashMap<>(results));
            data.put(Lg4jPlanExecutionState.STATUSES, new LinkedHashMap<>(statuses));
            data.put(Lg4jPlanExecutionState.ERRORS, new LinkedHashMap<>(errors));
            if (budget != null) {
                data.put(Lg4jPlanExecutionState.BUDGET, budget);
            }
            return new Lg4jPlanExecutionState(data);
        }

        private void apply(Map<String, Object> update) {
            results.putAll(map(update, Lg4jPlanExecutionState.RESULTS));
            statuses.putAll(map(update, Lg4jPlanExecutionState.STATUSES));
            errors.putAll(map(update, Lg4jPlanExecutionState.ERRORS));
            if (update.containsKey(Lg4jPlanExecutionState.BUDGET)) {
                budget = update.get(Lg4jPlanExecutionState.BUDGET);
            }
        }

        @SuppressWarnings("unchecked")
        private <T> Map<String, T> map(Map<String, Object> update, String key) {
            return (Map<String, T>) update.getOrDefault(key, Map.of());
        }
    }
}
