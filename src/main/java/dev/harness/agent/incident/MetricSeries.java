package dev.harness.agent.incident;

import java.io.Serializable;
import java.util.List;

public record MetricSeries(
        String id,
        String service,
        String metric,
        List<MetricPoint> points
) implements Serializable {
    public MetricSeries {
        points = points == null ? List.of() : List.copyOf(points);
    }
}
