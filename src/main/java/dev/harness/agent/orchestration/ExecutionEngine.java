package dev.harness.agent.orchestration;

import dev.harness.agent.execution.DagScheduler;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.ErrorClassifier;
import dev.harness.agent.run.RecoveryAction;
import dev.harness.agent.run.RecoveryPolicy;
import dev.harness.agent.run.RunStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

final class ExecutionEngine {

    private static final Logger log = LoggerFactory.getLogger(ExecutionEngine.class);

    private final DagScheduler scheduler;

    private final RunLifecycle lifecycle;

    private final int maxReplans;

    ExecutionEngine(DagScheduler scheduler, RunLifecycle lifecycle, int maxReplans) {
        this.scheduler = scheduler;
        this.lifecycle = lifecycle;
        this.maxReplans = Math.max(0, maxReplans);
    }

    RunState execute(RunState state) {
        try {
            log.info("Run {} execution started", state.context().runId());
            var executionResult = scheduler.execute(state.plan(), state.context().budget(),
                    (node, kind, latency, currentBudget) -> lifecycle.emitNodeEvent(
                            state, node, kind, latency, currentBudget));
            lifecycle.emit(state, "execution.finish", null, null,
                    executionResult.successful() ? "DONE" : "FAILED", "execution finished");
            log.info("Run {} execution finished with success={}", state.context().runId(), executionResult.successful());
            if (state.context().budget().exhausted()) {
                return lifecycle.finish(state, RunStatus.BUDGET_EXHAUSTED, null,
                        "budget exhausted after execution", ErrorClass.FATAL, null);
            }
            if (!executionResult.successful()) {
                return handleExecutionFailure(state);
            }

            return state.withPhase(RunPhase.VERIFYING);
        } catch (Exception exception) {
            log.warn("Run {} execution failed: {}", state.context().runId(), exception.getMessage());
            lifecycle.emit(state, "execution.finish", null, null, "FAILED", exception.getMessage());
            var action = RecoveryPolicy.decide(ErrorClass.FATAL);
            lifecycle.emitRecoveryDecision(state, ErrorClass.FATAL, action);
            return lifecycle.finish(state, RunStatus.FAILED_EXECUTION, null,
                    exception.getMessage(), ErrorClass.FATAL, null);
        }
    }

    RunState retry(RunState state) {
        return state.withPhase(RunPhase.EXECUTING);
    }

    private RunState handleExecutionFailure(RunState state) {
        var errorClass = classifyExecutionFailure(state.plan());
        var action = RecoveryPolicy.decide(errorClass);
        lifecycle.emitRecoveryDecision(state, errorClass, action);
        if (canReplan(action, state)) {
            return state.nextAttempt(buildExecutionFailureContext(state.plan(), errorClass));
        }
        return lifecycle.finish(state, RunStatus.FAILED_EXECUTION, null,
                "execution failed", errorClass, null);
    }

    private boolean canReplan(RecoveryAction action, RunState state) {
        return action == RecoveryAction.REPLAN && state.attempt() < maxReplans && state.context().budget().hasRoom();
    }

    private ErrorClass classifyExecutionFailure(Plan plan) {
        if (plan == null) {
            return ErrorClass.FATAL;
        }

        return failedNodes(plan).stream()
                .map(PlanNode::getErrorCode)
                .map(ErrorClassifier::classify)
                .reduce(ErrorClass.VALIDATION, ExecutionEngine::moreSevere);
    }

    private List<PlanNode> failedNodes(Plan plan) {
        return plan.nodes().stream()
                .filter(node -> node != null && node.isFailed())
                .toList();
    }

    private String buildExecutionFailureContext(Plan plan, ErrorClass errorClass) {
        if (plan == null) {
            return "execution failed: " + errorClass;
        }

        var failedNodes = failedNodes(plan).stream()
                .map(node -> "node=%s tool=%s errorCode=%s error=%s"
                        .formatted(node.getId(), node.getTool(), node.getErrorCode(), node.getError()))
                .collect(Collectors.joining("\n"));
        if (failedNodes.isBlank()) {
            return "execution failed: " + errorClass;
        }
        return "execution failed with %s:\n%s".formatted(errorClass, failedNodes);
    }

    private static ErrorClass moreSevere(ErrorClass left, ErrorClass right) {
        return severity(right) > severity(left) ? right : left;
    }

    private static int severity(ErrorClass errorClass) {
        return switch (errorClass) {
            case VALIDATION -> 0;
            case MISSING_INFO -> 1;
            case TRANSIENT -> 2;
            case FATAL -> 3;
        };
    }
}
