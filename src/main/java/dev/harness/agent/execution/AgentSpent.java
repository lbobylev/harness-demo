package dev.harness.agent.execution;

import dev.harness.agent.ai.AiUsage;

public record AgentSpent(
        AiUsage aiUsage
) {

    public AgentSpent {
        aiUsage = aiUsage == null ? AiUsage.none() : aiUsage;
    }

    public static AgentSpent none() {
        return new AgentSpent(AiUsage.none());
    }

    public static AgentSpent of(AiUsage aiUsage) {
        return new AgentSpent(aiUsage);
    }
}
