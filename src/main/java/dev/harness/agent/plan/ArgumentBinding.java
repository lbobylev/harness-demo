package dev.harness.agent.plan;

import java.io.Serializable;

public record ArgumentBinding(
        String argumentName,
        ArgumentValue value
) implements Serializable {
}
