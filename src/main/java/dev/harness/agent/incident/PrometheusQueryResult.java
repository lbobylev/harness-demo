package dev.harness.agent.incident;

public record PrometheusQueryResult(
        String service,
        String metric,
        String from,
        String to,
        MetricSeries series
) {
}
