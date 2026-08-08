package dev.harness.agent.execution;

import dev.harness.agent.plan.PlanNode;

record NodeExecutionOutcome(PlanNode node, boolean successful) {
}
