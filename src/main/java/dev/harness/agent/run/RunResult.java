package dev.harness.agent.run;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.plan.Plan;

public record RunResult(
        String runId,
        String sessionId,
        RunStatus status,
        String report,
        String error,
        ErrorClass errorClass,
        VerificationVerdict verdict,
        Plan plan,
        BudgetSnapshot budget) {
}
