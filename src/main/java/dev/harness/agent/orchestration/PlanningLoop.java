package dev.harness.agent.orchestration;

import dev.harness.agent.planning.Planner;
import dev.harness.agent.planning.PlanningRequest;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RecoveryAction;
import dev.harness.agent.run.RecoveryPolicy;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.validation.DagValidator;
import dev.harness.agent.validation.IncidentPolicyValidator;
import dev.harness.agent.validation.PlanValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class PlanningLoop {

    private static final Logger log = LoggerFactory.getLogger(PlanningLoop.class);

    private final Planner planner;

    private final DagValidator validator;

    private final IncidentPolicyValidator policyValidator;

    private final ToolCatalog toolCatalog;

    private final RunLifecycle lifecycle;

    private final int maxReplans;

    PlanningLoop(
            Planner planner,
            DagValidator validator,
            IncidentPolicyValidator policyValidator,
            ToolCatalog toolCatalog,
            RunLifecycle lifecycle,
            int maxReplans) {
        this.planner = planner;
        this.validator = validator;
        this.policyValidator = policyValidator;
        this.toolCatalog = toolCatalog;
        this.lifecycle = lifecycle;
        this.maxReplans = Math.max(0, maxReplans);
    }

    RunState plan(RunState state) {
        try {
            log.info("Run {} planning started for attempt {}", state.context().runId(), state.attempt());
            var planningResult = planner.plan(new PlanningRequest(
                    state.request().goal(),
                    toolCatalog.plannerCatalog(),
                    state.failureContext()));

            RunState next = state.withPlan(planningResult.plan());
            state.context().budget().chargeUsage(planningResult.usage());
            lifecycle.emit(next, "planning.finish", null, null, "DONE", "planning finished");
            log.info("Run {} planning finished for attempt {}", state.context().runId(), state.attempt());
            if (state.context().budget().exhausted()) {
                return lifecycle.finish(next, RunStatus.BUDGET_EXHAUSTED, null,
                        "budget exhausted after planning", ErrorClass.FATAL, null);
            }

            return next.withPhase(RunPhase.VALIDATING);
        } catch (Exception exception) {
            log.warn("Run {} planning failed: {}", state.context().runId(), exception.getMessage());
            lifecycle.emit(state, "planning.finish", null, null, "FAILED", exception.getMessage());
            return lifecycle.finish(state, RunStatus.FAILED_PLANNING, null,
                    exception.getMessage(), ErrorClass.FATAL, null);
        }
    }

    RunState validate(RunState state) {
        try {
            log.info("Run {} validation started", state.context().runId());
            validator.validate(state.plan());
            policyValidator.validate(state.plan());
            lifecycle.emit(state, "validation.finish", null, null, "DONE", "validation finished");
            log.info("Run {} validation finished", state.context().runId());
            return state.withPhase(RunPhase.EXECUTING);
        } catch (PlanValidationException exception) {
            log.warn("Run {} validation failed: {}", state.context().runId(), exception.getMessage());
            lifecycle.emit(state, "validation.finish", null, null, "FAILED", exception.getMessage());
            var action = RecoveryPolicy.decide(ErrorClass.VALIDATION);
            lifecycle.emitRecoveryDecision(state, ErrorClass.VALIDATION, action);
            if (canReplan(action, state)) {
                return state.nextAttempt("validation failed: " + exception.getMessage());
            }
            return lifecycle.finish(state, RunStatus.FAILED_VALIDATION, null,
                    exception.getMessage(), ErrorClass.VALIDATION, null);
        }
    }

    RunState replan(RunState state) {
        lifecycle.emit(state, "replanning.start", null, null, "STARTED", state.failureContext());
        return state.withPhase(RunPhase.PLANNING);
    }

    private boolean canReplan(RecoveryAction action, RunState state) {
        return action == RecoveryAction.REPLAN && state.attempt() < maxReplans && state.context().budget().hasRoom();
    }
}
