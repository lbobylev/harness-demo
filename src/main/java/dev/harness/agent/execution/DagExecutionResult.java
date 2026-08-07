package dev.harness.agent.execution;

import dev.harness.agent.plan.Plan;

public record DagExecutionResult(Plan plan, boolean successful) {
}
