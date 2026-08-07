package dev.harness.agent.run;

public enum RunStatus {
    SUCCEEDED,
    FAILED_PLANNING,
    FAILED_VALIDATION,
    FAILED_EXECUTION,
    FAILED_VERIFICATION,
    BUDGET_EXHAUSTED
}
