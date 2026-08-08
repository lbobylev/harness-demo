package dev.harness.agent.incident;

public record ConfigChange(
        String id,
        String timestamp,
        String service,
        String key,
        String oldValue,
        String newValue
) {
}
