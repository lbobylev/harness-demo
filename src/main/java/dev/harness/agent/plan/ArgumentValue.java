package dev.harness.agent.plan;

public record ArgumentValue(
        ArgumentValueType type,
        String literalValue,
        String sourceNodeId
) {
}
