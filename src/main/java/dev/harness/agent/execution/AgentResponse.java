package dev.harness.agent.execution;

import dev.harness.agent.ai.AiUsage;

public record AgentResponse(
        Object value,
        AiUsage usage
) {

    public AgentResponse {
        usage = usage == null ? AiUsage.none() : usage;
    }

    public static AgentResponse of(Object value) {
        return new AgentResponse(value, AiUsage.none());
    }
}
