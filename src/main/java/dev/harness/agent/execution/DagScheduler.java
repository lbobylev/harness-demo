package dev.harness.agent.execution;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.HarnessErrorCode;
import dev.harness.agent.tools.ToolExecutor;
import dev.harness.agent.tools.ToolExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import static dev.harness.agent.plan.NodeStatus.SKIPPED;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newFixedThreadPool;

@Component
public class DagScheduler {

    private final ToolExecutor toolExecutor;

    private final int maxConcurrency;

    public DagScheduler(
            ToolExecutor toolExecutor,
            @Value("${harness.execution.max-concurrency:5}") int maxConcurrency) {
        this.toolExecutor = toolExecutor;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public DagExecutionResult execute(Plan plan, Budget budget) {
        return execute(plan, budget, NodeExecutionListener.noop());
    }

    public DagExecutionResult execute(Plan plan, Budget budget, NodeExecutionListener listener) {
        requireNonNull(plan, "plan must not be null");
        requireNonNull(budget, "budget must not be null");
        NodeExecutionListener safeListener = listener == null ? NodeExecutionListener.noop() : listener;
        SchedulerState state = new SchedulerState(plan.nodes());

        var executorService = newFixedThreadPool(maxConcurrency);
        try {
            for (PlanNode node : state.initialReadyNodes()) {
                schedule(plan, budget, executorService, state, node, safeListener);
            }
            state.awaitCompletion();
        } finally {
            executorService.shutdownNow();
        }

        return new DagExecutionResult(plan, allDone(plan));
    }

    private void schedule(
            Plan plan,
            Budget budget,
            ExecutorService executorService,
            SchedulerState state,
            PlanNode node,
            NodeExecutionListener listener) {
        long startedAt = System.nanoTime();
        if (!budget.tryChargeToolCall()) {
            skipNode(plan, budget, executorService, state, node, listener, "budget exhausted", startedAt);
            return;
        }

        node.setStatus(NodeStatus.RUNNING);
        listener.onNodeEvent(node, "node.start", null, budget);
        try {
            executorService.submit(() -> {
                runNode(plan, budget, node, listener, startedAt);
                onNodeCompleted(plan, budget, executorService, state, new NodeExecutionOutcome(node, node.isDone()), listener);
            });
        } catch (RejectedExecutionException exception) {
            node.setError(exception.getMessage());
            node.setErrorCode(HarnessErrorCode.TOOL_EXECUTION_FAILED);
            node.setStatus(NodeStatus.FAILED);
            listener.onNodeEvent(node, "node.fail", elapsedSince(startedAt), budget);
            onNodeCompleted(plan, budget, executorService, state, new NodeExecutionOutcome(node, false), listener);
        }
    }

    private void runNode(
            Plan plan,
            Budget budget,
            PlanNode node,
            NodeExecutionListener listener,
            long startedAt) {
        try {
            var args = materializeArguments(plan, node);
            var result = toolExecutor.execute(node.getTool(), args);
            budget.chargeUsage(result.usage());
            node.setResult(result.value());
            node.setUsage(result.usage());
            node.setError(null);
            node.setErrorCode(null);
            node.setStatus(NodeStatus.DONE);
            listener.onNodeEvent(node, "node.finish", elapsedSince(startedAt), budget);
        } catch (ToolExecutionException exception) {
            node.setError(exception.getMessage());
            node.setErrorCode(exception.errorCode());
            node.setStatus(NodeStatus.FAILED);
            listener.onNodeEvent(node, "node.fail", elapsedSince(startedAt), budget);
        } catch (Exception exception) {
            node.setError(exception.getMessage());
            node.setErrorCode(HarnessErrorCode.TOOL_EXECUTION_FAILED);
            node.setStatus(NodeStatus.FAILED);
            listener.onNodeEvent(node, "node.fail", elapsedSince(startedAt), budget);
        }
    }

    private void onNodeCompleted(
            Plan plan,
            Budget budget,
            ExecutorService executorService,
            SchedulerState state,
            NodeExecutionOutcome outcome,
            NodeExecutionListener listener) {
        for (PlanNode child : state.dependentsOf(outcome.node())) {
            int remaining = state.dependencyCompleted(child, outcome.successful());
            if (remaining != 0) {
                continue;
            }

            if (state.hasFailedDependencies(child)) {
                skipNode(plan, budget, executorService, state, child, listener, "dependency failed", System.nanoTime());
            } else {
                schedule(plan, budget, executorService, state, child, listener);
            }
        }
        state.nodeTerminal();
    }

    private void skipNode(
            Plan plan,
            Budget budget,
            ExecutorService executorService,
            SchedulerState state,
            PlanNode node,
            NodeExecutionListener listener,
            String error,
            long startedAt) {
        node.setStatus(SKIPPED);
        node.setError(error);
        listener.onNodeEvent(node, "node.skip", elapsedSince(startedAt), budget);
        onNodeCompleted(plan, budget, executorService, state, new NodeExecutionOutcome(node, false), listener);
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private HashMap<String, Object> materializeArguments(Plan plan, PlanNode node) {
        var args = new HashMap<String, Object>();
        for (ArgumentBinding binding : node.getArguments()) {
            if (binding == null || binding.value() == null || binding.argumentName() == null) {
                continue;
            }
            Object value = materializeValue(plan, binding.value());
            if (value != null) {
                args.put(binding.argumentName(), value);
            }
        }
        return args;
    }

    private Object materializeValue(Plan plan, ArgumentValue value) {
        if (value.type() == ArgumentValueType.LITERAL) {
            return value.literalValue();
        }
        PlanNode sourceNode = plan.getNodeById(value.sourceNodeId());
        return sourceNode == null ? null : sourceNode.getResult();
    }

    private boolean allDone(Plan plan) {
        return plan.nodes().stream().allMatch(PlanNode::isDone);
    }
}
