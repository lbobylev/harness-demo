package dev.harness.lg4j;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.plan.Plan;

record Lg4jPlanningResult(
        Plan plan,
        AiUsage usage
) {
    Lg4jPlanningResult {
        usage = usage == null ? AiUsage.none() : usage;
    }
}
