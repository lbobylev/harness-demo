package dev.harness.lg4j.agents;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class Lg4jAgentSpecs {

    public static final String METRICS_AGENT = "MetricsAgent";
    public static final String LOGS_AGENT = "LogsAgent";
    public static final String TRACES_AGENT = "TracesAgent";
    public static final String DEPLOYMENTS_AGENT = "DeploymentsAgent";
    public static final String CONFIG_CHANGES_AGENT = "ConfigChangesAgent";
    public static final String METRIC_COMPARISON_AGENT = "MetricComparisonAgent";
    public static final String LOG_SIGNATURE_AGENT = "LogSignatureAgent";

    public static final String ARG_SERVICE = "service";
    public static final String ARG_METRIC = "metric";
    public static final String ARG_QUERY = "query";
    public static final String ARG_FROM = "from";
    public static final String ARG_TO = "to";
    public static final String ARG_METRIC_SERIES = "metricSeries";
    public static final String ARG_BASELINE_FROM = "baselineFrom";
    public static final String ARG_BASELINE_TO = "baselineTo";
    public static final String ARG_INCIDENT_FROM = "incidentFrom";
    public static final String ARG_INCIDENT_TO = "incidentTo";
    public static final String ARG_LOGS = "logs";

    private static final List<Lg4jAgentSpec> AGENTS = List.of(
            new Lg4jAgentSpec(METRICS_AGENT, "service, metric, from, to", "PrometheusQueryResult", "EVIDENCE", false),
            new Lg4jAgentSpec(LOGS_AGENT, "service, query, from, to", "LokiQueryResult", "EVIDENCE", false),
            new Lg4jAgentSpec(TRACES_AGENT, "service, query, from, to", "TempoQueryResult", "EVIDENCE", true),
            new Lg4jAgentSpec(DEPLOYMENTS_AGENT, "service, from, to", "List<DeploymentEvent>", "EVIDENCE", true),
            new Lg4jAgentSpec(CONFIG_CHANGES_AGENT, "service, from, to", "List<ConfigChange>", "EVIDENCE", true),
            new Lg4jAgentSpec(METRIC_COMPARISON_AGENT,
                    "metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo",
                    "PeriodComparison", "ANALYSIS", true),
            new Lg4jAgentSpec(LOG_SIGNATURE_AGENT, "logs", "LogSignature", "ANALYSIS", true));

    private Lg4jAgentSpecs() {
    }

    public static String promptCatalog() {
        return AGENTS.stream()
                .map(Lg4jAgentSpec::promptLine)
                .collect(Collectors.joining("\n"));
    }

    public static Set<String> names() {
        return AGENTS.stream()
                .map(Lg4jAgentSpec::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static Set<String> terminalNames() {
        return AGENTS.stream()
                .filter(Lg4jAgentSpec::terminal)
                .map(Lg4jAgentSpec::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
