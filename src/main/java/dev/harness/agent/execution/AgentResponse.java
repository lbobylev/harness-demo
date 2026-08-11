package dev.harness.agent.execution;

public record AgentResponse(
        Object value,
        AgentSpent spent
) {

    public AgentResponse {
        spent = spent == null ? AgentSpent.none() : spent;
    }

    public static AgentResponse of(Object value) {
        return new AgentResponse(value, AgentSpent.none());
    }
}
