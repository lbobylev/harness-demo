package dev.harness.agent.incident;

import java.io.Serializable;

public record MetricPoint(
        String id,
        String timestamp,
        double value
) implements Serializable {
}
