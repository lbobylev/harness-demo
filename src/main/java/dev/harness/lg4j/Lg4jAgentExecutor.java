package dev.harness.lg4j;

import dev.harness.agent.execution.AgentExecutionException;
import dev.harness.agent.execution.AgentResponse;
import dev.harness.agent.incident.LokiQueryResult;
import dev.harness.agent.incident.PrometheusQueryResult;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_BASELINE_FROM;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_BASELINE_TO;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_FROM;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_INCIDENT_FROM;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_INCIDENT_TO;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_LOGS;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_METRIC;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_METRIC_SERIES;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_QUERY;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_SERVICE;
import static dev.harness.lg4j.Lg4jAgentSpecs.ARG_TO;
import static dev.harness.lg4j.Lg4jAgentSpecs.CONFIG_CHANGES_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.DEPLOYMENTS_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.LOGS_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.LOG_SIGNATURE_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.METRICS_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.METRIC_COMPARISON_AGENT;
import static dev.harness.lg4j.Lg4jAgentSpecs.TRACES_AGENT;

@Component
class Lg4jAgentExecutor {

    private final MetricsAgent metricsAgent;
    private final LogsAgent logsAgent;
    private final TracesAgent tracesAgent;
    private final DeploymentsAgent deploymentsAgent;
    private final ConfigChangesAgent configChangesAgent;
    private final MetricComparisonAgent metricComparisonAgent;
    private final LogSignatureAgent logSignatureAgent;

    Lg4jAgentExecutor(
            MetricsAgent metricsAgent,
            LogsAgent logsAgent,
            TracesAgent tracesAgent,
            DeploymentsAgent deploymentsAgent,
            ConfigChangesAgent configChangesAgent,
            MetricComparisonAgent metricComparisonAgent,
            LogSignatureAgent logSignatureAgent) {
        this.metricsAgent = metricsAgent;
        this.logsAgent = logsAgent;
        this.tracesAgent = tracesAgent;
        this.deploymentsAgent = deploymentsAgent;
        this.configChangesAgent = configChangesAgent;
        this.metricComparisonAgent = metricComparisonAgent;
        this.logSignatureAgent = logSignatureAgent;
    }

    AgentResponse execute(String name, Map<String, Object> args) {
        var safeArgs = args == null ? Map.<String, Object>of() : args;
        return switch (name) {
            case METRICS_AGENT -> AgentResponse.of(metricsAgent.query(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_METRIC), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case LOGS_AGENT -> AgentResponse.of(logsAgent.query(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_QUERY), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case TRACES_AGENT -> AgentResponse.of(tracesAgent.query(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_QUERY), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case DEPLOYMENTS_AGENT -> AgentResponse.of(deploymentsAgent.query(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case CONFIG_CHANGES_AGENT -> AgentResponse.of(configChangesAgent.query(
                    text(safeArgs, ARG_SERVICE), text(safeArgs, ARG_FROM), text(safeArgs, ARG_TO)));
            case METRIC_COMPARISON_AGENT -> AgentResponse.of(metricComparisonAgent.compare(
                    value(safeArgs, ARG_METRIC_SERIES, PrometheusQueryResult.class),
                    text(safeArgs, ARG_BASELINE_FROM), text(safeArgs, ARG_BASELINE_TO),
                    text(safeArgs, ARG_INCIDENT_FROM), text(safeArgs, ARG_INCIDENT_TO)));
            case LOG_SIGNATURE_AGENT -> AgentResponse.of(logSignatureAgent.find(
                    value(safeArgs, ARG_LOGS, LokiQueryResult.class)));
            default -> throw new AgentExecutionException("unknown lg4j agent: " + name);
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
