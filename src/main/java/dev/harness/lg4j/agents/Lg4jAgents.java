package dev.harness.lg4j.agents;

import dev.harness.agent.incident.ConfigChange;
import dev.harness.agent.incident.DeploymentEvent;
import dev.harness.agent.incident.LogEvent;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.LokiQueryResult;
import dev.harness.agent.incident.MetricPoint;
import dev.harness.agent.incident.MetricSeries;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.PrometheusQueryResult;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.incident.TraceSpan;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MetricsAgent {

    PrometheusQueryResult query(String service, String metric, String from, String to) {
        var safeService = Lg4jAgentDefaults.text(service, "checkout-service");
        var safeMetric = Lg4jAgentDefaults.text(metric, "5xx_rate");
        var series = new MetricSeries("metric.%s.%s".formatted(safeService, safeMetric), safeService, safeMetric,
                List.of(
                        new MetricPoint("metric-point-1", "14:30", 0.01),
                        new MetricPoint("metric-point-2", "14:35", 0.22),
                        new MetricPoint("metric-point-3", "14:40", 0.31)));
        return new PrometheusQueryResult(safeService, safeMetric,
                Lg4jAgentDefaults.text(from, "14:30"), Lg4jAgentDefaults.text(to, "14:45"), series);
    }
}

@Component
class LogsAgent {

    LokiQueryResult query(String service, String query, String from, String to) {
        var safeService = Lg4jAgentDefaults.text(service, "checkout-service");
        return new LokiQueryResult(safeService, Lg4jAgentDefaults.text(query, "error timeout"),
                Lg4jAgentDefaults.text(from, "14:30"), Lg4jAgentDefaults.text(to, "14:45"), List.of(
                new LogEvent("log-1", "14:33", safeService, "ERROR", "4.18.2", "catalog-service timeout"),
                new LogEvent("log-2", "14:36", safeService, "ERROR", "4.18.2", "catalog-service timeout"),
                new LogEvent("log-3", "14:39", safeService, "WARN", "4.18.2", "slow catalog response")));
    }
}

@Component
class TracesAgent {

    TempoQueryResult query(String service, String query, String from, String to) {
        var safeService = Lg4jAgentDefaults.text(service, "checkout-service");
        return new TempoQueryResult(safeService, Lg4jAgentDefaults.text(query, "catalog"),
                Lg4jAgentDefaults.text(from, "14:30"), Lg4jAgentDefaults.text(to, "14:45"), List.of(
                new TraceSpan("span-1", "trace-1", "14:35", safeService, "checkout", "GET /checkout",
                        "catalog-service", 1800, "ERROR", "downstream timeout")));
    }
}

@Component
class DeploymentsAgent {

    List<DeploymentEvent> query(String service, String from, String to) {
        var safeService = Lg4jAgentDefaults.text(service, "checkout-service");
        return List.of(new DeploymentEvent("deploy-1", "14:28", safeService, "4.18.2", "change-1"));
    }
}

@Component
class ConfigChangesAgent {

    List<ConfigChange> query(String service, String from, String to) {
        var safeService = Lg4jAgentDefaults.text(service, "checkout-service");
        return List.of(new ConfigChange("config-1", "14:29", safeService, "catalog.timeout.ms", "1000", "250"));
    }
}

@Component
class MetricComparisonAgent {

    PeriodComparison compare(
            PrometheusQueryResult metricSeries,
            String baselineFrom,
            String baselineTo,
            String incidentFrom,
            String incidentTo) {
        var metricId = metricSeries == null || metricSeries.series() == null
                ? "metric.checkout-service.5xx_rate"
                : metricSeries.series().id();
        return new PeriodComparison(metricId, 0.01, 0.28, 0.27, "INCREASED",
                List.of("metric-point-1", "metric-point-2", "metric-point-3"));
    }
}

@Component
class LogSignatureAgent {

    LogSignature find(LokiQueryResult logs) {
        var service = logs == null ? "checkout-service" : logs.service();
        return new LogSignature("catalog-service timeout", "14:33", 3, "ERROR", service,
                List.of("log-1", "log-2", "log-3"));
    }
}

final class Lg4jAgentDefaults {

    private Lg4jAgentDefaults() {
    }

    static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
