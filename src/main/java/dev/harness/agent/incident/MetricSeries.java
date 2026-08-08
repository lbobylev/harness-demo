package dev.harness.agent.incident;

import java.util.List;

public record MetricSeries(
        String id,
        String service,
        String metric,
        List<MetricPoint> points
) {
    public MetricSeries {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
