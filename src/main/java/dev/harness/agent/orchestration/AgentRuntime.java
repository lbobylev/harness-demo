package dev.harness.agent.orchestration;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.verification.ReportVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final PlanningLoop planningLoop;

    private final ExecutionEngine executionEngine;

    private final RunLifecycle lifecycle;

    private final ReportVerifier verifier;

    AgentRuntime(
            PlanningLoop planningLoop,
            ExecutionEngine executionEngine,
            RunLifecycle lifecycle,
            ReportVerifier verifier) {
        this.planningLoop = planningLoop;
        this.executionEngine = executionEngine;
        this.lifecycle = lifecycle;
        this.verifier = verifier;
    }

    RunResult run(RunRequest request) {
        RunState state = lifecycle.create(request);
        while (!state.terminal()) {
            state = transition(state);
        }
        return state.result();
    }

    private RunState transition(RunState state) {
        return switch (state.phase()) {
            case CREATED -> lifecycle.start(state);
            case PLANNING -> planningLoop.plan(state);
            case VALIDATING -> planningLoop.validate(state);
            case EXECUTING -> executionEngine.execute(state);
            case VERIFYING -> verify(state);
            case REPLANNING -> planningLoop.replan(state);
            case RETRYING -> executionEngine.retry(state);
            case SUCCEEDED, FAILED, BUDGET_EXHAUSTED -> throw new IllegalStateException(
                    "terminal phase reached without a run result: " + state.phase());
        };
    }

    private RunState verify(RunState state) {
        VerificationVerdict verdict;
        try {
            log.info("Run {} verification started", state.context().runId());
            verdict = verifier.verify(state.plan());
        } catch (Exception exception) {
            log.warn("Run {} verification failed: {}", state.context().runId(), exception.getMessage());
            lifecycle.emit(state, "verification.finish", null, null, "FAILED", exception.getMessage());
            return lifecycle.finish(state, RunStatus.FAILED_VERIFICATION, null,
                    exception.getMessage(), ErrorClass.FATAL, null);
        }
        lifecycle.emit(state, "verification.finish", null, null,
                verdict.passed() ? "DONE" : "FAILED", verdict.reason());
        log.info("Run {} verification finished with passed={}", state.context().runId(), verdict.passed());
        if (!verdict.passed()) {
            return lifecycle.finish(state, RunStatus.FAILED_VERIFICATION, null,
                    verdict.reason(), ErrorClass.VALIDATION, verdict);
        }

        return lifecycle.finish(state, RunStatus.SUCCEEDED, lifecycle.finalReport(state.plan()), null,
                null, verdict);
    }
}
