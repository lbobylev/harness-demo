package dev.harness.agent.tools;

public record ToolDefinitionView(
        String name,
        String description,
        String inputSchema,
        ToolRole role
) {
}
