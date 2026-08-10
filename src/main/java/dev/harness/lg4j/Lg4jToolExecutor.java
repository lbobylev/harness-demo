package dev.harness.lg4j;

import dev.harness.agent.incident.LokiQueryResult;
import dev.harness.agent.incident.PrometheusQueryResult;
import dev.harness.agent.tools.ToolExecutionException;
import dev.harness.agent.tools.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_BASELINE_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_BASELINE_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_INCIDENT_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_INCIDENT_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_LOGS;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC_SERIES;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_QUERY;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_SERVICE;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.COMPARE_PERIODS;
import static dev.harness.agent.tools.IncidentInvestigationTools.FIND_LOG_SIGNATURE;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_CONFIG_CHANGES;
import static dev.harness.agent.tools.IncidentInvestigationTools.GET_DEPLOYMENTS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_LOKI;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_PROMETHEUS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_TEMPO;

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

}
