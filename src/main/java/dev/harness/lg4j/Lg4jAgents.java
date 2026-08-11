package dev.harness.lg4j;

import dev.harness.agent.incident.ConfigChange;
import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.DeploymentEvent;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.IncidentReport;
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

@Component
class EvidenceCorrelationAgent {

    CorrelationResult correlate(EvidenceBundle evidence) {
        var evidenceIds = evidence == null ? List.of("metric-point-1", "log-1", "span-1") : evidence.evidenceIds();
        return new CorrelationResult(
                List.of(
                        "14:28 checkout-service deployed version 4.18.2",
                        "14:33 checkout-service logs show catalog-service timeout",
                        "14:35 trace trace-1 spent 1800ms in GET /checkout"),
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("database degradation was not observed"),
                evidenceIds);
    }
}

@Component
class HypothesisAssessmentAgent {

    HypothesisAssessment assess(String hypothesis, CorrelationResult evidence) {
        return new HypothesisAssessment(
                Lg4jAgentDefaults.text(hypothesis, "catalog-service latency caused checkout-service 5xx"),
                "STRONG",
                0.89,
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("checkout deployment is time-correlated but weaker"),
                List.of(),
                "SUPPORTED",
                evidence == null ? List.of("metric-point-1", "log-1", "span-1") : evidence.evidenceIds());
    }
}

@Component
class IncidentReportAgent {

    IncidentReport build(String incident, HypothesisAssessment hypothesisAssessment, CorrelationResult evidence) {
        return new IncidentReport(
                "catalog-service degradation caused checkout-service 5xx through downstream timeouts",
                0.60,
                evidence == null ? new EvidenceCorrelationAgent().correlate(null).timeline() : evidence.timeline(),
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("checkout deployment regression", "database degradation"),
                "Mitigate catalog-service degradation or temporarily degrade checkout catalog enrichment path");
    }
}

final class Lg4jAgentDefaults {

    private Lg4jAgentDefaults() {
    }

    static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
