package dev.harness.agent.incident;

public record LogEvent(
        String id,
        String timestamp,
        String service,
        String level,
        String version,
        String message
) {
}
