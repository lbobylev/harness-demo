package dev.harness.agent.run;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.trace.TraceEvent;

import java.util.List;

public record RunResult(
        String runId,
        String sessionId,
        RunStatus status,
        String report,
        String error,
        ErrorClass errorClass,
        VerificationVerdict verdict,
        Plan plan,
        List<TraceEvent> traceEvents,
        BudgetSnapshot budget
) {

    public RunResult {
        traceEvents = traceEvents == null ? List.of() : List.copyOf(traceEvents);
    }
}
