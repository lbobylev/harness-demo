package dev.harness.agent.orchestration;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.RunResult;

record RunState(
        RunPhase phase,
        RunRequest request,
        RunContext context,
        Plan plan,
        String failureContext,
        int attempt,
        RunResult result
) {

    boolean terminal() {
        return result != null;
    }

    RunState withPhase(RunPhase nextPhase) {
        return new RunState(nextPhase, request, context, plan, failureContext, attempt, result);
    }

    RunState withPlan(Plan nextPlan) {
        return new RunState(phase, request, context, nextPlan, failureContext, attempt, result);
    }

    RunState nextAttempt(String nextFailureContext) {
        return new RunState(RunPhase.REPLANNING, request, context, plan, nextFailureContext, attempt + 1, result);
    }

    RunState finished(RunResult nextResult) {
        return new RunState(phase, request, context, plan, failureContext, attempt, nextResult);
    }
}
