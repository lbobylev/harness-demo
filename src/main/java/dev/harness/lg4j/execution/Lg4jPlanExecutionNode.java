package dev.harness.lg4j.execution;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.lg4j.graph.Lg4jDebugValue;
import dev.harness.lg4j.incident.Lg4jIncidentAnalysis;
import dev.harness.lg4j.state.Lg4jPlanExecutionState;
import dev.harness.lg4j.state.Lg4jRunState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Lg4jPlanExecutionNode {

    private static final Logger log = LoggerFactory.getLogger(Lg4jPlanExecutionNode.class);

    private final Lg4jPlanExecutor planExecutor;

    public Lg4jPlanExecutionNode(Lg4jPlanExecutor planExecutor) {
        this.planExecutor = planExecutor;
    }

    public Map<String, Object> execute(Lg4jRunState state, Budget budget) {
        if (state.terminal()) {
            return Map.of();
        }

        var plan = state.plan().orElse(null);
        if (plan == null) {
            return failure("plan must not be null");
        }

        if (budget == null) {
            return failure("budget must be present");
        }
        try {
            var executionState = planExecutor.execute(plan, budget);
            applyExecutionState(plan, executionState);
            var analysis = finalAnalysis(executionState);

            if (budget.exhausted()) {
                return Map.of(
                        Lg4jRunState.PLAN, plan,
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "budget exhausted after execution");
            }
            if (hasFailedOrSkippedNode(plan) || hasFailedOrSkippedRuntimeTail(executionState)) {
                return Map.of(
                        Lg4jRunState.PLAN, plan,
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "execution failed");
            }
            if (analysis == null) {
                return Map.of(
                        Lg4jRunState.PLAN, plan,
                        Lg4jRunState.BUDGET, budget.snapshot(),
                        Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                        Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                        Lg4jRunState.ERROR, "incident analysis was not produced");
            }

            return Map.of(
                    Lg4jRunState.PLAN, plan,
                    Lg4jRunState.INCIDENT_ANALYSIS, analysis,
                    Lg4jRunState.BUDGET, budget.snapshot());
        } catch (Exception exception) {
            log.warn("LangGraph4j plan execution failed: plan={} error={}",
                    Lg4jDebugValue.dump(plan), Lg4jDebugValue.dump(exception), exception);
            return Map.of(
                    Lg4jRunState.BUDGET, budget.snapshot(),
                    Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                    Lg4jRunState.ERROR, Lg4jDebugValue.dump(exception));
        }
    }

    private void applyExecutionState(Plan plan, Lg4jPlanExecutionState executionState) {
        for (var node : plan.nodes()) {
            var nodeId = node.getId();
            node.setStatus(executionState.statuses().getOrDefault(nodeId, node.getStatus()));
            node.setResult(executionState.result(nodeId));
            node.setError(executionState.errors().get(nodeId));
        }
    }

    private boolean hasFailedOrSkippedNode(Plan plan) {
        return plan.nodes().stream().anyMatch(node -> node != null && (node.isFailed() || node.isSkipped()));
    }

    private boolean hasFailedOrSkippedRuntimeTail(Lg4jPlanExecutionState executionState) {
        return isFailedOrSkipped(executionState, Lg4jPlanExecutionState.ANALYZE_EVIDENCE);
    }

    private boolean isFailedOrSkipped(Lg4jPlanExecutionState executionState, String nodeId) {
        var status = executionState.statuses().get(nodeId);
        return status == NodeStatus.FAILED || status == NodeStatus.SKIPPED;
    }

    private Lg4jIncidentAnalysis finalAnalysis(Lg4jPlanExecutionState executionState) {
        var result = executionState.result(Lg4jPlanExecutionState.ANALYZE_EVIDENCE);
        if (result instanceof Lg4jIncidentAnalysis analysis) {
            return analysis;
        }
        return null;
    }

    private Map<String, Object> failure(String error) {
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                Lg4jRunState.ERROR, error);
    }
}
