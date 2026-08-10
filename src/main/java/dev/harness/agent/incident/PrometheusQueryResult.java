package dev.harness.agent.incident;

import java.io.Serializable;

public record PrometheusQueryResult(
        String service,
        String metric,
        String from,
        String to,
        MetricSeries series
) implements Serializable {
}
