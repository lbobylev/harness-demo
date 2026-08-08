package dev.harness.agent.orchestration;

enum RunPhase {
    CREATED,
    PLANNING,
    VALIDATING,
    EXECUTING,
    VERIFYING,
    REPLANNING,
    RETRYING,
    SUCCEEDED,
    FAILED,
    BUDGET_EXHAUSTED
}
