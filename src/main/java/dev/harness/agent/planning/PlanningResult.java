package dev.harness.agent.planning;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.plan.Plan;

public record PlanningResult(
        Plan plan,
        AiUsage usage
) {
}
