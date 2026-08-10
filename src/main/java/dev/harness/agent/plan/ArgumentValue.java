package dev.harness.agent.plan;

import java.io.Serializable;

public record ArgumentValue(
        ArgumentValueType type,
        String literalValue,
        String sourceNodeId
) implements Serializable {
}
