package dev.harness.agent.tools;

import dev.harness.agent.ai.AiUsage;

public record ToolExecutionResult(
        Object value,
        AiUsage usage
) {

    public ToolExecutionResult {
        usage = usage == null ? AiUsage.none() : usage;
    }

    public static ToolExecutionResult of(Object value) {
        return new ToolExecutionResult(value, AiUsage.none());
    }
}
