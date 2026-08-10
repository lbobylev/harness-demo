package dev.harness.lg4j;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.harness.agent.tools.IncidentInvestigationTools.COMPARE_PERIODS;
import static dev.harness.agent.tools.IncidentInvestigationTools.FIND_LOG_SIGNATURE;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_CONFIG_CHANGES;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_DEPLOYMENTS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_LOKI;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_PROMETHEUS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_TEMPO;

final class Lg4jToolSpecs {

    private static final List<Lg4jToolSpec> TOOLS = List.of(
            new Lg4jToolSpec(QUERY_PROMETHEUS, "service, metric, from, to", "PrometheusQueryResult", "EVIDENCE", false),
            new Lg4jToolSpec(QUERY_LOKI, "service, query, from, to", "LokiQueryResult", "EVIDENCE", false),
            new Lg4jToolSpec(QUERY_TEMPO, "service, query, from, to", "TempoQueryResult", "EVIDENCE", true),
            new Lg4jToolSpec(GET_DEPLOYMENTS, "service, from, to", "List<DeploymentEvent>", "EVIDENCE", true),
            new Lg4jToolSpec(GET_CONFIG_CHANGES, "service, from, to", "List<ConfigChange>", "EVIDENCE", true),
            new Lg4jToolSpec(COMPARE_PERIODS,
                    "metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo",
                    "PeriodComparison", "ANALYSIS", true),
            new Lg4jToolSpec(FIND_LOG_SIGNATURE, "logs", "LogSignature", "ANALYSIS", true));

    private Lg4jToolSpecs() {
    }

    static String promptCatalog() {
        return TOOLS.stream()
                .map(Lg4jToolSpec::promptLine)
                .collect(Collectors.joining("\n"));
    }

    static Set<String> names() {
        return TOOLS.stream()
                .map(Lg4jToolSpec::name)
                .collect(Collectors.toUnmodifiableSet());
    }

    static Set<String> terminalNames() {
        return TOOLS.stream()
                .filter(Lg4jToolSpec::terminal)
                .map(Lg4jToolSpec::name)
                .collect(Collectors.toUnmodifiableSet());
    }
}
