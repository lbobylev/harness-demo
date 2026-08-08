package dev.harness.agent.incident;

public record MetricPoint(
        String id,
        String timestamp,
        double value
) {
}
