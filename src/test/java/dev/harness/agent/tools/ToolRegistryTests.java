package dev.harness.agent.tools;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.IncidentData;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.LokiQueryResult;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.PrometheusQueryResult;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.run.HarnessErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTests {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        IncidentInvestigationTools tools = new IncidentInvestigationTools(new IncidentData());
        registry = new ToolRegistry(tools, new ToolCatalog(tools));
    }

    @Test
    void queriesPrometheusFixture() {
        ToolExecutionResult result = registry.execute("query_prometheus", Map.of(
                "service", "checkout-service",
                "metric", "5xx_rate",
                "from", "14:20",
                "to", "14:40"));

        assertThat(result.usage()).isEqualTo(dev.harness.agent.ai.AiUsage.none());
        assertThat(result.value()).isInstanceOf(PrometheusQueryResult.class);
        PrometheusQueryResult queryResult = (PrometheusQueryResult) result.value();
        assertThat(queryResult.series().id()).isEqualTo("metric.checkout.5xx");
        assertThat(queryResult.series().points()).isNotEmpty();
    }

    @Test
    void rejectsUnknownTool() {
        assertThatThrownBy(() -> registry.execute("delete_database", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo(HarnessErrorCode.UNKNOWN_TOOL);
        assertThatThrownBy(() -> registry.execute("delete_database", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void rejectsMissingRequiredIncidentArg() {
        assertThatThrownBy(() -> registry.execute("query_prometheus", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT);
        assertThatThrownBy(() -> registry.execute("query_prometheus", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("service");
    }

    @Test
    void analyzesTelemetryAndBuildsReport() {
        PrometheusQueryResult metric = (PrometheusQueryResult) registry.execute("query_prometheus", Map.of(
                "service", "catalog-service",
                "metric", "latency_p95",
                "from", "14:20",
                "to", "14:40")).value();
        PeriodComparison comparison = (PeriodComparison) registry.execute("compare_periods", Map.of(
                "metricSeries", metric,
                "baselineFrom", "14:20",
                "baselineTo", "14:30",
                "incidentFrom", "14:31",
                "incidentTo", "14:40")).value();
        LokiQueryResult logs = (LokiQueryResult) registry.execute("query_loki", Map.of(
                "service", "checkout-service",
                "query", "error timeout catalog",
                "from", "14:20",
                "to", "14:40")).value();
        LogSignature signature = (LogSignature) registry.execute("find_log_signature", Map.of("logs", logs)).value();
        TempoQueryResult traces = (TempoQueryResult) registry.execute("query_tempo", Map.of(
                "service", "checkout-service",
                "query", "catalog error",
                "from", "14:20",
                "to", "14:40")).value();
        EvidenceBundle bundle = (EvidenceBundle) registry.execute("assemble_evidence", Map.of(
                "metricComparison", comparison,
                "logSignature", signature,
                "traces", traces,
                "deployments", registry.execute("get_deployments", Map.of(
                        "service", "checkout-service", "from", "14:00", "to", "14:40")).value(),
                "configChanges", registry.execute("get_config_changes", Map.of(
                        "service", "catalog-service", "from", "14:00", "to", "14:40")).value())).value();
        CorrelationResult correlation = (CorrelationResult) registry.execute("correlate", Map.of("evidence", bundle)).value();
        HypothesisAssessment assessment = (HypothesisAssessment) registry.execute("test_hypothesis", Map.of(
                "hypothesis", "catalog-service degradation caused checkout failures",
                "evidence", correlation)).value();
        IncidentReport report = (IncidentReport) registry.execute("build_incident_report", Map.of(
                "incident", "Checkout 5xx increased after 14:32",
                "hypothesisAssessment", assessment,
                "evidence", correlation)).value();

        assertThat(comparison.change()).isEqualTo("INCREASED");
        assertThat(signature.signature()).contains("catalog-service timeout");
        assertThat(assessment.decision()).isEqualTo("SUPPORTED");
        assertThat(report.rootCause()).contains("catalog-service degradation");
    }

    @Test
    void testHypothesisSteersCheckoutChangeHypothesisTowardCatalog() {
        CorrelationResult correlation = correlateCatalogEvidence();

        assertThatThrownBy(() -> registry.execute("test_hypothesis", Map.of(
                "hypothesis", "checkout-service deployment or config change introduced a failure path",
                "evidence", correlation)))
                .isInstanceOf(ToolExecutionException.class)
                .satisfies(exception -> {
                    ToolExecutionException toolException = (ToolExecutionException) exception;
                    assertThat(toolException.errorCode()).isEqualTo(HarnessErrorCode.MISSING_INFO);
                    assertThat(toolException.getMessage()).contains("test catalog-service degradation next");
                });
    }

    @Test
    void assembleEvidenceIgnoresEmptyTraceMap() {
        EvidenceBundle bundle = assembleEvidenceWithTraces(Map.of());

        assertThat(bundle.traces()).isEmpty();
        assertThat(bundle.evidenceIds()).contains("metric.catalog.latency", "log.checkout.catalog-timeout.1");
    }

    @Test
    void assembleEvidenceIgnoresEmptyTraceLiteral() {
        EvidenceBundle bundle = assembleEvidenceWithTraces("{}");

        assertThat(bundle.traces()).isEmpty();
        assertThat(bundle.evidenceIds()).contains("metric.catalog.latency", "log.checkout.catalog-timeout.1");
    }

    @Test
    void assembleEvidenceRejectsNonEmptyWrongTraceType() {
        assertThatThrownBy(() -> assembleEvidenceWithTraces(Map.of("traceId", "trc-001")))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo(HarnessErrorCode.INVALID_ARGUMENT);
    }

    private EvidenceBundle assembleEvidenceWithTraces(Object traces) {
        PrometheusQueryResult metric = (PrometheusQueryResult) registry.execute("query_prometheus", Map.of(
                "service", "catalog-service",
                "metric", "latency_p95",
                "from", "14:20",
                "to", "14:40")).value();
        PeriodComparison comparison = (PeriodComparison) registry.execute("compare_periods", Map.of(
                "metricSeries", metric,
                "baselineFrom", "14:20",
                "baselineTo", "14:30",
                "incidentFrom", "14:31",
                "incidentTo", "14:40")).value();
        LokiQueryResult logs = (LokiQueryResult) registry.execute("query_loki", Map.of(
                "service", "checkout-service",
                "query", "error timeout catalog",
                "from", "14:20",
                "to", "14:40")).value();
        LogSignature signature = (LogSignature) registry.execute("find_log_signature", Map.of("logs", logs)).value();

        return (EvidenceBundle) registry.execute("assemble_evidence", Map.of(
                "metricComparison", comparison,
                "logSignature", signature,
                "traces", traces,
                "deployments", registry.execute("get_deployments", Map.of(
                        "service", "checkout-service", "from", "14:00", "to", "14:40")).value(),
                "configChanges", registry.execute("get_config_changes", Map.of(
                        "service", "catalog-service", "from", "14:00", "to", "14:40")).value())).value();
    }

    private CorrelationResult correlateCatalogEvidence() {
        return (CorrelationResult) registry.execute("correlate", Map.of(
                "evidence", assembleEvidenceWithTraces("{}"))).value();
    }
}
