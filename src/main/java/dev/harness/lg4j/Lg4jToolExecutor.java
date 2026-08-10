package dev.harness.lg4j;

import dev.harness.agent.incident.ConfigChange;
import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.DeploymentEvent;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.LokiQueryResult;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.PrometheusQueryResult;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.tools.ToolExecutionException;
import dev.harness.agent.tools.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_BASELINE_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_BASELINE_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_CONFIG_CHANGES;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_DEPLOYMENTS;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_EVIDENCE;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_HYPOTHESIS;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_HYPOTHESIS_ASSESSMENT;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_INCIDENT;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_INCIDENT_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_INCIDENT_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_LOGS;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_LOG_SIGNATURE;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC_COMPARISON;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC_SERIES;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_QUERY;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_SERVICE;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_TRACES;
import static dev.harness.agent.tools.IncidentInvestigationTools.ASSEMBLE_EVIDENCE;
import static dev.harness.agent.tools.IncidentInvestigationTools.BUILD_INCIDENT_REPORT;
import static dev.harness.agent.tools.IncidentInvestigationTools.COMPARE_PERIODS;
import static dev.harness.agent.tools.IncidentInvestigationTools.CORRELATE;
import static dev.harness.agent.tools.IncidentInvestigationTools.FIND_LOG_SIGNATURE;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_CONFIG_CHANGES;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_DEPLOYMENTS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_LOKI;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_PROMETHEUS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_TEMPO;
import static dev.harness.agent.tools.IncidentInvestigationTools.TEST_HYPOTHESIS;

@Component
class Lg4jToolExecutor {

    private final Lg4jTools tools;

    Lg4jToolExecutor(Lg4jTools tools) {
        this.tools = tools;
    }

    ToolExecutionResult execute(String name, Map<String, Object> args) {
        var safeArgs = args == null ? Map.<String, Object>of() : args;
        return switch (name) {
            case QUERY_PROMETHEUS -> ToolExecutionResult.of(tools.queryPrometheus(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_METRIC), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case QUERY_LOKI -> ToolExecutionResult.of(tools.queryLoki(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_QUERY), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case QUERY_TEMPO -> ToolExecutionResult.of(tools.queryTempo(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_QUERY), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case GET_DEPLOYMENTS -> ToolExecutionResult.of(tools.getDeployments(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case GET_CONFIG_CHANGES -> ToolExecutionResult.of(tools.getConfigChanges(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case COMPARE_PERIODS -> ToolExecutionResult.of(tools.comparePeriods(
                    value(safeArgs, ARG_METRIC_SERIES, PrometheusQueryResult.class),
                    text(safeArgs, ARG_BASELINE_FROM), text(safeArgs, ARG_BASELINE_TO),
                    text(safeArgs, ARG_INCIDENT_FROM), text(safeArgs, ARG_INCIDENT_TO)));
            case FIND_LOG_SIGNATURE -> ToolExecutionResult.of(tools.findLogSignature(
                    value(safeArgs, ARG_LOGS, LokiQueryResult.class)));
            case ASSEMBLE_EVIDENCE -> ToolExecutionResult.of(tools.assembleEvidence(
                    value(safeArgs, ARG_METRIC_COMPARISON, PeriodComparison.class),
                    value(safeArgs, ARG_LOG_SIGNATURE, LogSignature.class),
                    value(safeArgs, ARG_TRACES, TempoQueryResult.class),
                    list(safeArgs, ARG_DEPLOYMENTS, DeploymentEvent.class),
                    list(safeArgs, ARG_CONFIG_CHANGES, ConfigChange.class)));
            case CORRELATE -> ToolExecutionResult.of(tools.correlate(
                    value(safeArgs, ARG_EVIDENCE, EvidenceBundle.class)));
            case TEST_HYPOTHESIS -> ToolExecutionResult.of(tools.testHypothesis(
                    text(safeArgs, ARG_HYPOTHESIS), value(safeArgs, ARG_EVIDENCE, CorrelationResult.class)));
            case BUILD_INCIDENT_REPORT -> ToolExecutionResult.of(tools.buildIncidentReport(
                    text(safeArgs, ARG_INCIDENT),
                    value(safeArgs, ARG_HYPOTHESIS_ASSESSMENT, HypothesisAssessment.class),
                    value(safeArgs, ARG_EVIDENCE, CorrelationResult.class)));
            default -> throw new ToolExecutionException("unknown lg4j tool: " + name);
        };
    }

    private static String text(Map<String, Object> args, String key) {
        var value = args.get(key);
        return value instanceof String text ? text : null;
    }

    private static <T> T value(Map<String, Object> args, String key, Class<T> type) {
        var value = args.get(key);
        return type.isInstance(value) ? type.cast(value) : null;
    }

    private static <T> List<T> list(Map<String, Object> args, String key, Class<T> type) {
        var value = args.get(key);
        if (value instanceof List<?> list && list.stream().allMatch(type::isInstance)) {
            return list.stream().map(type::cast).toList();
        }
        return List.of();
    }
}
