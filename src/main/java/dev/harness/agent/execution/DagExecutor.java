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

import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newFixedThreadPool;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static dev.harness.agent.plan.NodeStatus.FAILED;
import static dev.harness.agent.plan.NodeStatus.PENDING;
import static dev.harness.agent.plan.NodeStatus.SKIPPED;

@Component
public class DagExecutor {

    private final ToolExecutor toolExecutor;

    private final int maxConcurrency;

    public DagExecutor(
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

        var executorService = newFixedThreadPool(maxConcurrency);
        try {
            while (hasPendingNodes(plan)) {
                if (budget.exhausted()) {
                    skipBudgetExhaustedPendingNodes(plan);
                    break;
                }
                var readyNodes = readyNodes(plan);
                if (readyNodes.isEmpty()) {
                    skipBlockedPendingNodes(plan);
                    break;
                }
                runReadyNodes(plan, budget, executorService, readyNodes, safeListener);
            }
        } finally {
            executorService.shutdownNow();
        }

        return new DagExecutionResult(plan, allDone(plan));
    }

    private void runReadyNodes(
            Plan plan,
            Budget budget,
            ExecutorService executorService,
            List<PlanNode> readyNodes,
            NodeExecutionListener listener) {
        var futures = new ArrayList<Future<?>>();
        for (PlanNode node : readyNodes) {
            futures.add(executorService.submit(() -> runNode(plan, budget, node, listener)));
        }
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("DAG execution interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("DAG execution failed unexpectedly", exception);
            }
        }
    }

    private void runNode(Plan plan, Budget budget, PlanNode node, NodeExecutionListener listener) {
        long startedAt = System.nanoTime();
        node.setStatus(NodeStatus.RUNNING);
        budget.chargeToolCall();
        listener.onNodeEvent(node, "node.start", null, budget);
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

    private boolean hasBlockedDependency(Plan plan, PlanNode node) {
        return plan.getDepNodes(node).stream()
                .anyMatch(dep -> dep != null && List.of(FAILED, SKIPPED, PENDING).contains(dep.getStatus()));
    }

    private boolean hasPendingNodes(Plan plan) {
        return plan.nodes().stream().anyMatch(PlanNode::isPending);
    }

    private List<PlanNode> readyNodes(Plan plan) {
        return plan.nodes().stream()
                .filter(node -> node.isPending()
                        && plan.getDepNodes(node).stream().allMatch(dep -> dep != null && dep.isDone()))
                .toList();
    }

    private void skipBlockedPendingNodes(Plan plan) {
        for (var node : plan.nodes()) {
            if (node.isPending() && hasBlockedDependency(plan, node)) {
                node.setStatus(SKIPPED);
                node.setError("dependency failed");
            }
        }
    }

    private void skipBudgetExhaustedPendingNodes(Plan plan) {
        for (var node : plan.nodes()) {
            if (node.isPending()) {
                node.setStatus(SKIPPED);
                node.setError("budget exhausted");
            }
        }
    }

}
