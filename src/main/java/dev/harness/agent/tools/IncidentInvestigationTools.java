package dev.harness.agent.tools;

import dev.harness.agent.incident.ConfigChange;
import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.DeploymentEvent;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.IncidentData;
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
import dev.harness.agent.run.HarnessErrorCode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class IncidentInvestigationTools {

    public static final String QUERY_PROMETHEUS = "query_prometheus";
    public static final String QUERY_LOKI = "query_loki";
    public static final String QUERY_TEMPO = "query_tempo";
    public static final String GET_DEPLOYMENTS = "get_deployments";
    public static final String GET_CONFIG_CHANGES = "get_config_changes";
    public static final String COMPARE_PERIODS = "compare_periods";
    public static final String FIND_LOG_SIGNATURE = "find_log_signature";
    public static final String ASSEMBLE_EVIDENCE = "assemble_evidence";
    public static final String CORRELATE = "correlate";
    public static final String TEST_HYPOTHESIS = "test_hypothesis";
    public static final String BUILD_INCIDENT_REPORT = "build_incident_report";

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
    public static final String ARG_METRIC_COMPARISON = "metricComparison";
    public static final String ARG_LOG_SIGNATURE = "logSignature";
    public static final String ARG_TRACES = "traces";
    public static final String ARG_DEPLOYMENTS = "deployments";
    public static final String ARG_CONFIG_CHANGES = "configChanges";
    public static final String ARG_EVIDENCE = "evidence";
    public static final String ARG_HYPOTHESIS = "hypothesis";
    public static final String ARG_INCIDENT = "incident";
    public static final String ARG_HYPOTHESIS_ASSESSMENT = "hypothesisAssessment";

    private static final Pattern REQUEST_ID = Pattern.compile("requestId=[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACE_ID = Pattern.compile("traceId=[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION = Pattern.compile("\\b\\d+ms\\b");
    private static final Pattern NUMBER_ASSIGNMENT = Pattern.compile("\\b(workers|duration|active|idle)=\\d+\\b", Pattern.CASE_INSENSITIVE);

    private final IncidentData data;

    public IncidentInvestigationTools(IncidentData data) {
        this.data = data;
    }

    @Tool(name = QUERY_PROMETHEUS, description = "Query synthetic Prometheus metric data by service, metric, and bounded time window.")
    public PrometheusQueryResult queryPrometheus(
            @ToolParam(description = "Service name, such as checkout-service or catalog-service") String service,
            @ToolParam(description = "Metric name, such as 5xx_rate, latency_p95, or request_rate") String metric,
            @ToolParam(description = "Start time in HH:mm format") String from,
            @ToolParam(description = "End time in HH:mm format") String to) {
        MetricSeries series = data.metrics().stream()
                .filter(candidate -> same(candidate.service(), service) && same(candidate.metric(), metric))
                .findFirst()
                .map(candidate -> new MetricSeries(candidate.id(), candidate.service(), candidate.metric(),
                        candidate.points().stream()
                                .filter(point -> within(point.timestamp(), from, to))
                                .toList()))
                .orElse(new MetricSeries("metric.%s.%s.empty".formatted(service, metric), service, metric, List.of()));
        return new PrometheusQueryResult(service, metric, from, to, series);
    }

    @Tool(name = QUERY_LOKI, description = "Query synthetic Loki logs by service, query text, and bounded time window.")
    public LokiQueryResult queryLoki(
            @ToolParam(description = "Service name") String service,
            @ToolParam(description = "Simple case-insensitive query terms, such as error timeout catalog") String query,
            @ToolParam(description = "Start time in HH:mm format") String from,
            @ToolParam(description = "End time in HH:mm format") String to) {
        List<LogEvent> entries = data.logs().stream()
                .filter(log -> same(log.service(), service))
                .filter(log -> within(log.timestamp(), from, to))
                .filter(log -> matchesQuery(query, log.level(), log.message(), log.version()))
                .toList();
        return new LokiQueryResult(service, query, from, to, entries);
    }

    @Tool(name = QUERY_TEMPO, description = "Query synthetic Tempo trace spans by service, query text, and bounded time window.")
    public TempoQueryResult queryTempo(
            @ToolParam(description = "Service name") String service,
            @ToolParam(description = "Simple case-insensitive query terms for operation, span, downstream, status, or error") String query,
            @ToolParam(description = "Start time in HH:mm format") String from,
            @ToolParam(description = "End time in HH:mm format") String to) {
        List<TraceSpan> spans = data.traces().stream()
                .filter(span -> same(span.service(), service))
                .filter(span -> within(span.timestamp(), from, to))
                .filter(span -> matchesQuery(query, span.operation(), span.spanName(), span.downstreamService(), span.status(), span.error()))
                .toList();
        return new TempoQueryResult(service, query, from, to, spans);
    }

    @Tool(name = GET_DEPLOYMENTS, description = "Get synthetic deployment events by service and bounded time window.")
    public List<DeploymentEvent> getDeployments(
            @ToolParam(description = "Service name") String service,
            @ToolParam(description = "Start time in HH:mm format") String from,
            @ToolParam(description = "End time in HH:mm format") String to) {
        return data.deployments().stream()
                .filter(deployment -> same(deployment.service(), service))
                .filter(deployment -> within(deployment.timestamp(), from, to))
                .toList();
    }

    @Tool(name = GET_CONFIG_CHANGES, description = "Get synthetic config changes by service and bounded time window.")
    public List<ConfigChange> getConfigChanges(
            @ToolParam(description = "Service name") String service,
            @ToolParam(description = "Start time in HH:mm format") String from,
            @ToolParam(description = "End time in HH:mm format") String to) {
        return data.configChanges().stream()
                .filter(change -> same(change.service(), service))
                .filter(change -> within(change.timestamp(), from, to))
                .toList();
    }

    @Tool(name = COMPARE_PERIODS, description = "Compare metric baseline and incident windows.")
    public PeriodComparison comparePeriods(
            @ToolParam(description = "Prometheus metric query result") PrometheusQueryResult metricSeries,
            @ToolParam(description = "Baseline start time in HH:mm format") String baselineFrom,
            @ToolParam(description = "Baseline end time in HH:mm format") String baselineTo,
            @ToolParam(description = "Incident start time in HH:mm format") String incidentFrom,
            @ToolParam(description = "Incident end time in HH:mm format") String incidentTo) {
        MetricSeries series = metricSeries == null ? null : metricSeries.series();
        if (series == null) {
            return new PeriodComparison("unknown", 0.0, 0.0, 0.0, "UNCHANGED", List.of());
        }

        double baselineAverage = average(series.points(), baselineFrom, baselineTo);
        double incidentAverage = average(series.points(), incidentFrom, incidentTo);
        double delta = incidentAverage - baselineAverage;
        String change = classifyChange(baselineAverage, delta);
        List<String> evidenceIds = Stream.concat(
                        Stream.of(series.id()),
                        series.points().stream()
                                .filter(point -> within(point.timestamp(), baselineFrom, baselineTo)
                                        || within(point.timestamp(), incidentFrom, incidentTo))
                                .map(MetricPoint::id))
                .toList();
        return new PeriodComparison(series.id(), baselineAverage, incidentAverage, delta, change, evidenceIds);
    }

    @Tool(name = FIND_LOG_SIGNATURE, description = "Find the dominant repeated warning or error pattern in Loki logs.")
    public LogSignature findLogSignature(@ToolParam(description = "Loki query result") LokiQueryResult logs) {
        if (logs == null || logs.entries().isEmpty()) {
            return new LogSignature("", "", 0, "", logs == null ? "" : logs.service(), List.of());
        }

        Map<String, List<LogEvent>> groups = logs.entries().stream()
                .filter(log -> "ERROR".equalsIgnoreCase(log.level()) || "WARN".equalsIgnoreCase(log.level()))
                .collect(LinkedHashMap::new,
                        (map, log) -> map.computeIfAbsent(normalizeMessage(log.message()), ignored -> new ArrayList<>()).add(log),
                        Map::putAll);

        List<LogEvent> dominant = groups.values().stream()
                .max(Comparator.comparingInt(List::size))
                .orElse(List.of());
        if (dominant.isEmpty()) {
            return new LogSignature("", "", 0, "", logs.service(), List.of());
        }

        LogEvent first = dominant.stream()
                .min(Comparator.comparing(LogEvent::timestamp))
                .orElse(dominant.getFirst());
        return new LogSignature(
                normalizeMessage(first.message()),
                first.timestamp(),
                dominant.size(),
                first.level(),
                first.service(),
                dominant.stream().map(LogEvent::id).toList());
    }

    @Tool(name = ASSEMBLE_EVIDENCE, description = "Assemble metric, log, trace, deployment, and config evidence into one bundle.")
    public EvidenceBundle assembleEvidence(
            @ToolParam(description = "Metric comparison evidence") PeriodComparison metricComparison,
            @ToolParam(description = "Log signature evidence") LogSignature logSignature,
            @ToolParam(description = "Tempo trace query result") TempoQueryResult traces,
            @ToolParam(description = "Deployment events") List<DeploymentEvent> deployments,
            @ToolParam(description = "Config change events") List<ConfigChange> configChanges) {
        List<String> evidenceIds = Stream.of(
                        metricComparison == null ? Stream.<String>empty() : metricComparison.evidenceIds().stream(),
                        logSignature == null ? Stream.<String>empty() : logSignature.evidenceIds().stream(),
                        traces == null ? Stream.<String>empty() : traces.spans().stream().map(TraceSpan::id),
                        deployments == null ? Stream.<String>empty() : deployments.stream().map(DeploymentEvent::id),
                        configChanges == null ? Stream.<String>empty() : configChanges.stream().map(ConfigChange::id))
                .flatMap(stream -> stream)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return new EvidenceBundle(
                metricComparison == null ? List.of() : List.of(metricComparison),
                logSignature == null ? List.of() : List.of(logSignature),
                traces == null ? List.of() : List.of(traces),
                deployments == null ? List.of() : List.copyOf(deployments),
                configChanges == null ? List.of() : List.copyOf(configChanges),
                evidenceIds);
    }

    @Tool(name = CORRELATE, description = "Correlate incident evidence into timeline, correlations, and contradictions.")
    public CorrelationResult correlate(@ToolParam(description = "Evidence bundle") EvidenceBundle evidence) {
        if (evidence == null) {
            return new CorrelationResult(List.of(), List.of(), List.of("no evidence bundle was provided"), List.of());
        }

        List<String> timeline = new ArrayList<>();
        List<String> correlations = new ArrayList<>();
        List<String> contradictions = new ArrayList<>();

        evidence.deployments().forEach(deployment -> timeline.add("%s %s deployed version %s"
                .formatted(deployment.timestamp(), deployment.service(), deployment.version())));
        evidence.configChanges().forEach(change -> timeline.add("%s %s changed %s from %s to %s"
                .formatted(change.timestamp(), change.service(), change.key(), change.oldValue(), change.newValue())));
        evidence.metricComparisons().forEach(comparison -> addMetricCorrelation(comparison, timeline, correlations, contradictions));
        evidence.logSignatures().stream()
                .filter(signature -> signature.count() > 0)
                .forEach(signature -> {
                    timeline.add("%s %s logs show %s".formatted(signature.firstSeen(), signature.service(), signature.signature()));
                    correlations.add("%s repeated %d times in %s logs"
                            .formatted(signature.signature(), signature.count(), signature.service()));
                });
        evidence.traces().stream()
                .flatMap(result -> result.spans().stream())
                .filter(span -> span.durationMs() >= 1000 || "ERROR".equalsIgnoreCase(span.status()))
                .forEach(span -> {
                    timeline.add("%s trace %s spent %dms in %s"
                            .formatted(span.timestamp(), span.traceId(), span.durationMs(), span.spanName()));
                    correlations.add("failed or slow trace points to %s".formatted(span.downstreamService()));
                });

        return new CorrelationResult(timeline, correlations, contradictions, evidence.evidenceIds());
    }

    @Tool(name = TEST_HYPOTHESIS, description = "Test an incident hypothesis against structured evidence.")
    public HypothesisAssessment testHypothesis(
            @ToolParam(description = "Hypothesis to test") String hypothesis,
            @ToolParam(description = "Correlated evidence") CorrelationResult evidence) {
        String normalizedHypothesis = normalize(hypothesis);
        String evidenceText = evidenceText(evidence);
        boolean hasCatalogEvidence = evidenceText.contains("catalog-service")
                && (evidenceText.contains("timeout") || evidenceText.contains("latency") || evidenceText.contains("slow trace"));
        boolean hasCheckoutDeploy = evidenceText.contains("checkout-service deployed") || evidenceText.contains("4.18.2");
        boolean hasCheckout5xx = evidenceText.contains("metric.checkout.5xx") || evidenceText.contains("5xx");

        if (isCheckoutChangeHypothesis(normalizedHypothesis) && hasCatalogEvidence) {
            throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO,
                    "evidence weakens checkout deployment/config hypothesis; test catalog-service degradation next");
        }
        if (isCheckoutChangeHypothesis(normalizedHypothesis)) {
            throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO,
                    "missing evidence: inspect catalog-service metrics/logs/traces around 14:31-14:40");
        }
        if (normalizedHypothesis.contains("catalog") && hasCatalogEvidence) {
            return new HypothesisAssessment(
                    hypothesis,
                    "STRONG",
                    0.89,
                    List.of(
                            "catalog-service latency or errors increased before checkout failures",
                            "checkout logs contain repeated catalog-service timeout evidence",
                            "slow or failed traces point to catalog-service"),
                    hasCheckoutDeploy ? List.of("checkout deployment is time-correlated but weaker than catalog-service evidence") : List.of(),
                    List.of(),
                    "SUPPORTED",
                    evidence == null ? List.of() : evidence.evidenceIds());
        }
        if (normalizedHypothesis.contains("database")) {
            return new HypothesisAssessment(
                    hypothesis,
                    "WEAK",
                    0.21,
                    List.of(),
                    List.of("database latency remained normal during the incident window"),
                    hasCheckout5xx ? List.of("inspect catalog-service evidence as the next likely dependency") : List.of("inspect checkout and dependency evidence"),
                    "REJECTED",
                    evidence == null ? List.of() : evidence.evidenceIds());
        }

        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO,
                "missing evidence: gather metric, log, trace, deployment, and config evidence for the proposed hypothesis");
    }

    @Tool(name = BUILD_INCIDENT_REPORT, description = "Build the final evidence-backed incident report from a supported hypothesis assessment.")
    public IncidentReport buildIncidentReport(
            @ToolParam(description = "Incident description") String incident,
            @ToolParam(description = "Supported hypothesis assessment") HypothesisAssessment hypothesisAssessment,
            @ToolParam(description = "Correlated evidence") CorrelationResult evidence) {
        List<String> timeline = evidence == null ? List.of() : evidence.timeline();
        List<String> evidenceItems = new ArrayList<>();
        if (hypothesisAssessment != null) {
            evidenceItems.addAll(hypothesisAssessment.evidenceFor());
            evidenceItems.addAll(hypothesisAssessment.evidenceAgainst());
        }
        if (evidenceItems.isEmpty() && evidence != null) {
            evidenceItems.addAll(evidence.correlations());
            evidenceItems.addAll(evidence.contradictions());
        }

        String rootCause = hypothesisAssessment == null || hypothesisAssessment.hypothesis() == null
                ? "unknown"
                : "catalog-service degradation caused checkout-service 5xx through downstream timeouts";
        double confidence = hypothesisAssessment == null ? 0.0 : hypothesisAssessment.confidence();
        return new IncidentReport(
                rootCause,
                confidence,
                timeline,
                evidenceItems,
                List.of("checkout deployment regression", "database degradation"),
                "Mitigate catalog-service degradation or temporarily degrade checkout catalog enrichment path");
    }

    private static boolean same(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private static boolean within(String timestamp, String from, String to) {
        String value = minute(timestamp);
        String start = minute(from);
        String end = minute(to);
        return value.compareTo(start) >= 0 && value.compareTo(end) <= 0;
    }

    private static String minute(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return "";
        }
        return timestamp.length() >= 5 ? timestamp.substring(0, 5) : timestamp;
    }

    private static boolean matchesQuery(String query, String... values) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank() || normalizedQuery.equals("*") || normalizedQuery.equals("all")) {
            return true;
        }
        String haystack = Stream.of(values)
                .filter(Objects::nonNull)
                .map(IncidentInvestigationTools::normalize)
                .reduce("", (left, right) -> left + " " + right);
        for (String token : normalizedQuery.split("\\s+")) {
            if (!token.isBlank() && haystack.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static double average(List<MetricPoint> points, String from, String to) {
        return points.stream()
                .filter(point -> within(point.timestamp(), from, to))
                .mapToDouble(MetricPoint::value)
                .average()
                .orElse(0.0);
    }

    private static String classifyChange(double baselineAverage, double delta) {
        double threshold = Math.max(Math.abs(baselineAverage) * 0.25, 0.01);
        if (delta > threshold) {
            return "INCREASED";
        }
        if (delta < -threshold) {
            return "DECREASED";
        }
        return "UNCHANGED";
    }

    private static String normalizeMessage(String message) {
        String result = message == null ? "" : message;
        result = REQUEST_ID.matcher(result).replaceAll("requestId=<id>");
        result = TRACE_ID.matcher(result).replaceAll("traceId=<id>");
        result = DURATION.matcher(result).replaceAll("<duration>");
        result = NUMBER_ASSIGNMENT.matcher(result).replaceAll(match -> match.group(1) + "=<number>");
        return result;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static void addMetricCorrelation(
            PeriodComparison comparison,
            List<String> timeline,
            List<String> correlations,
            List<String> contradictions) {
        if (comparison.metricId() == null || comparison.metricId().isBlank()) {
            return;
        }
        if ("INCREASED".equals(comparison.change())) {
            correlations.add("%s increased from %.3f to %.3f"
                    .formatted(comparison.metricId(), comparison.baselineAverage(), comparison.incidentAverage()));
            timeline.add("incident window %s increased".formatted(comparison.metricId()));
            return;
        }
        if ("UNCHANGED".equals(comparison.change())) {
            contradictions.add("%s stayed near baseline".formatted(comparison.metricId()));
        }
    }

    private static String evidenceText(CorrelationResult evidence) {
        if (evidence == null) {
            return "";
        }
        return normalize(String.join(" ", Stream.of(
                        evidence.timeline(),
                        evidence.correlations(),
                        evidence.contradictions(),
                        evidence.evidenceIds())
                .flatMap(List::stream)
                .toList()));
    }

    private static boolean isCheckoutChangeHypothesis(String hypothesis) {
        return hypothesis.contains("deploy")
                || hypothesis.contains("deployment")
                || hypothesis.contains("config")
                || hypothesis.contains("configuration")
                || hypothesis.contains("checkout regression")
                || hypothesis.contains("checkout-service regression");
    }
}
