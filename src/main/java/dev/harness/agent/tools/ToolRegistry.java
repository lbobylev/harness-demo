package dev.harness.agent.tools;

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
import dev.harness.agent.run.HarnessErrorCode;
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
public class ToolRegistry implements ToolExecutor {

    private final IncidentInvestigationTools tools;

    private final ToolCatalog catalog;

    public ToolRegistry(IncidentInvestigationTools tools, ToolCatalog catalog) {
        this.tools = tools;
        this.catalog = catalog;
    }

    public boolean hasTool(String name) {
        return catalog.hasTool(name);
    }

    @Override
    public ToolExecutionResult execute(String name, Map<String, Object> args) {
        if (!hasTool(name)) {
            throw new ToolExecutionException(HarnessErrorCode.UNKNOWN_TOOL, "unknown tool: " + name);
        }

        Map<String, Object> safeArgs = args == null ? Map.of() : args;

        return switch (name) {
            case QUERY_PROMETHEUS -> ToolExecutionResult.of(tools.queryPrometheus(
                    requiredString(safeArgs, ARG_SERVICE),
                    requiredString(safeArgs, ARG_METRIC),
                    requiredString(safeArgs, ARG_FROM),
                    requiredString(safeArgs, ARG_TO)));
            case QUERY_LOKI -> ToolExecutionResult.of(tools.queryLoki(
                    requiredString(safeArgs, ARG_SERVICE),
                    requiredString(safeArgs, ARG_QUERY),
                    requiredString(safeArgs, ARG_FROM),
                    requiredString(safeArgs, ARG_TO)));
            case QUERY_TEMPO -> ToolExecutionResult.of(tools.queryTempo(
                    requiredString(safeArgs, ARG_SERVICE),
                    requiredString(safeArgs, ARG_QUERY),
                    requiredString(safeArgs, ARG_FROM),
                    requiredString(safeArgs, ARG_TO)));
            case GET_DEPLOYMENTS -> ToolExecutionResult.of(tools.getDeployments(
                    requiredString(safeArgs, ARG_SERVICE),
                    requiredString(safeArgs, ARG_FROM),
                    requiredString(safeArgs, ARG_TO)));
            case GET_CONFIG_CHANGES -> ToolExecutionResult.of(tools.getConfigChanges(
                    requiredString(safeArgs, ARG_SERVICE),
                    requiredString(safeArgs, ARG_FROM),
                    requiredString(safeArgs, ARG_TO)));
            case COMPARE_PERIODS -> ToolExecutionResult.of(tools.comparePeriods(
                    requiredType(safeArgs, ARG_METRIC_SERIES, PrometheusQueryResult.class),
                    requiredString(safeArgs, ARG_BASELINE_FROM),
                    requiredString(safeArgs, ARG_BASELINE_TO),
                    requiredString(safeArgs, ARG_INCIDENT_FROM),
                    requiredString(safeArgs, ARG_INCIDENT_TO)));
            case FIND_LOG_SIGNATURE -> ToolExecutionResult.of(tools.findLogSignature(
                    requiredType(safeArgs, ARG_LOGS, LokiQueryResult.class)));
            case ASSEMBLE_EVIDENCE -> ToolExecutionResult.of(tools.assembleEvidence(
                    requiredType(safeArgs, ARG_METRIC_COMPARISON, PeriodComparison.class),
                    requiredType(safeArgs, ARG_LOG_SIGNATURE, LogSignature.class),
                    optionalType(safeArgs, ARG_TRACES, TempoQueryResult.class),
                    requiredList(safeArgs, ARG_DEPLOYMENTS, DeploymentEvent.class),
                    requiredList(safeArgs, ARG_CONFIG_CHANGES, ConfigChange.class)));
            case CORRELATE -> ToolExecutionResult.of(tools.correlate(
                    requiredType(safeArgs, ARG_EVIDENCE, EvidenceBundle.class)));
            case TEST_HYPOTHESIS -> ToolExecutionResult.of(tools.testHypothesis(
                    requiredString(safeArgs, ARG_HYPOTHESIS),
                    requiredType(safeArgs, ARG_EVIDENCE, CorrelationResult.class)));
            case BUILD_INCIDENT_REPORT -> ToolExecutionResult.of(tools.buildIncidentReport(
                    requiredString(safeArgs, ARG_INCIDENT),
                    requiredType(safeArgs, ARG_HYPOTHESIS_ASSESSMENT, HypothesisAssessment.class),
                    requiredType(safeArgs, ARG_EVIDENCE, CorrelationResult.class)));
            default -> throw new ToolExecutionException("unhandled tool: " + name);
        };
    }

    private static String requiredString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new ToolExecutionException(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT,
                "missing required string arg: " + key);
    }

    private static <T> T requiredType(Map<String, Object> args, String key, Class<T> type) {
        Object value = args.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new ToolExecutionException(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT,
                "missing required arg %s of type %s".formatted(key, type.getSimpleName()));
    }

    private static <T> T optionalType(Map<String, Object> args, String key, Class<T> type) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        if (value instanceof String text && (text.isBlank() || "{}".equals(text.trim()))) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        throw new ToolExecutionException(HarnessErrorCode.INVALID_ARGUMENT,
                "invalid optional arg %s of type %s".formatted(key, type.getSimpleName()));
    }

    private static <T> List<T> requiredList(Map<String, Object> args, String key, Class<T> elementType) {
        Object value = args.get(key);
        if (value instanceof List<?> list && list.stream().allMatch(elementType::isInstance)) {
            return list.stream().map(elementType::cast).toList();
        }
        throw new ToolExecutionException(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT,
                "missing required list arg %s of type %s".formatted(key, elementType.getSimpleName()));
    }
}
