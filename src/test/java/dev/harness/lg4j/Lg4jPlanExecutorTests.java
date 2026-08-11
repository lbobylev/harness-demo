package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.tools.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import static org.assertj.core.api.Assertions.assertThat;

class Lg4jPlanExecutorTests {

    private static final String ANALYZE_EVIDENCE = Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE;

    @Test
    void doesNotExecuteMoreReadyNodesThanRemainingToolCallBudget() {
        var budget = budget(100, 1);
        var tools = new Lg4jTools();
        var executor = new Lg4jPlanExecutor(
                new Lg4jToolExecutor(tools),
                new Lg4jEvidenceAnalysisNode(tools),
                4);
        var plan = new Plan(List.of(
                new PlanNode("metrics", "query_prometheus", List.of()),
                new PlanNode("logs", "query_loki", List.of())));

        var state = executor.execute(plan, budget);

        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(1L);
        assertThat(state.statuses().entrySet())
                .filteredOn(entry -> List.of("metrics", "logs").contains(entry.getKey()))
                .extracting(Map.Entry::getValue)
                .containsExactlyInAnyOrder(NodeStatus.DONE, NodeStatus.SKIPPED);
        assertThat(state.errors()).containsValue("budget exhausted");
    }

    @Test
    void executesSingleTerminalPlannerNode() {
        var toolExecutor = new RecordingToolExecutor();
        var executor = executor(toolExecutor);
        var plan = new Plan(List.of(node("traces", QUERY_TEMPO, literals(
                ARG_SERVICE, "checkout-service",
                ARG_QUERY, "catalog",
                ARG_FROM, "14:00",
                ARG_TO, "14:45"))));

        var state = executor.execute(plan, budget(10_000, 20));

        assertThat(state.errors()).isEmpty();
        assertThat(state.statuses())
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.DONE);
        assertThat(toolExecutor.callsTo(QUERY_TEMPO)).hasSize(1);
        assertThat(state.result(ANALYZE_EVIDENCE)).isInstanceOf(Lg4jIncidentAnalysis.class);
    }

    @Test
    void executesPlannerDagAndPassesNodeResultsToDownstreamTools() {
        var toolExecutor = new RecordingToolExecutor();
        var executor = executor(toolExecutor);
        var plan = fullEvidencePlan();

        var state = executor.execute(plan, budget(10_000, 20));

        assertThat(state.errors()).isEmpty();
        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("comparison", NodeStatus.DONE)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("signature", NodeStatus.DONE)
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry("deployments", NodeStatus.DONE)
                .containsEntry("configs", NodeStatus.DONE)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.DONE);

        assertThat(toolExecutor.argsFor("comparison").get(ARG_METRIC_SERIES))
                .isEqualTo(state.result("metrics"));
        assertThat(toolExecutor.argsFor("signature").get(ARG_LOGS))
                .isEqualTo(state.result("logs"));
        assertThat(state.result("comparison")).isNotNull();
        assertThat(state.result("signature")).isNotNull();
        assertThat(state.result(ANALYZE_EVIDENCE))
                .isInstanceOfSatisfying(Lg4jIncidentAnalysis.class, analysis -> {
                    assertThat(analysis.evidence().metricComparisons())
                            .containsExactly((PeriodComparison) state.result("comparison"));
                    assertThat(analysis.evidence().logSignatures())
                            .containsExactly((LogSignature) state.result("signature"));
                    assertThat(analysis.evidence().traces())
                            .containsExactly((TempoQueryResult) state.result("traces"));
                    assertThat(analysis.evidence().deployments()).isEqualTo(state.result("deployments"));
                    assertThat(analysis.evidence().configChanges()).isEqualTo(state.result("configs"));
                });
    }

    @Test
    void supportsFanOutFromOnePlannerNodeToMultipleConsumers() {
        var toolExecutor = new RecordingToolExecutor();
        var executor = executor(toolExecutor);
        var plan = new Plan(List.of(
                node("metrics", QUERY_PROMETHEUS, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("baselineComparison", COMPARE_PERIODS, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("recentComparison", COMPARE_PERIODS, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "14:00"),
                        lit(ARG_BASELINE_TO, "14:20"),
                        lit(ARG_INCIDENT_FROM, "14:20"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics")));

        var state = executor.execute(plan, budget(10_000, 20));

        assertThat(state.errors()).isEmpty();
        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("baselineComparison", NodeStatus.DONE)
                .containsEntry("recentComparison", NodeStatus.DONE)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.DONE);
        assertThat(toolExecutor.callsTo(QUERY_PROMETHEUS)).hasSize(1);
        assertThat(toolExecutor.argsFor("baselineComparison").get(ARG_METRIC_SERIES))
                .isEqualTo(state.result("metrics"));
        assertThat(toolExecutor.argsFor("recentComparison").get(ARG_METRIC_SERIES))
                .isEqualTo(state.result("metrics"));
        assertThat(state.result(ANALYZE_EVIDENCE))
                .isInstanceOfSatisfying(Lg4jIncidentAnalysis.class, analysis ->
                        assertThat(analysis.evidence().metricComparisons())
                                .containsExactlyInAnyOrder(
                                        (PeriodComparison) state.result("baselineComparison"),
                                        (PeriodComparison) state.result("recentComparison")));
    }

    @Test
    void skipsDependentNodesAfterFailureAndStillRunsIndependentPlannerBranches() {
        var toolExecutor = new RecordingToolExecutor(QUERY_PROMETHEUS);
        var executor = executor(toolExecutor);
        var plan = new Plan(List.of(
                node("metrics", QUERY_PROMETHEUS, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("comparison", COMPARE_PERIODS, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("logs", QUERY_LOKI, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "error timeout",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("signature", FIND_LOG_SIGNATURE, List.of(ref(ARG_LOGS, "logs")), "logs"),
                node("traces", QUERY_TEMPO, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "catalog",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45"))));

        var state = executor.execute(plan, budget(10_000, 20));

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.FAILED)
                .containsEntry("comparison", NodeStatus.SKIPPED)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("signature", NodeStatus.DONE)
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.SKIPPED);
        assertThat(state.errors())
                .containsEntry("metrics", "planned failure: query_prometheus")
                .containsEntry("comparison", "dependency failed")
                .containsEntry(ANALYZE_EVIDENCE, "dependency failed");
        assertThat(toolExecutor.callsTo(COMPARE_PERIODS)).isEmpty();
        assertThat(toolExecutor.callsTo(QUERY_LOKI)).hasSize(1);
        assertThat(toolExecutor.callsTo(FIND_LOG_SIGNATURE)).hasSize(1);
        assertThat(toolExecutor.callsTo(QUERY_TEMPO)).hasSize(1);
    }

    @Test
    void skipsToolCallWhenWallClockBudgetExpires() {
        var tools = new Lg4jTools();
        var executor = new Lg4jPlanExecutor(
                new SlowToolExecutor(Duration.ofMillis(200)),
                new Lg4jEvidenceAnalysisNode(tools),
                1);
        var plan = new Plan(List.of(node("traces", QUERY_TEMPO, literals(
                ARG_SERVICE, "checkout-service",
                ARG_QUERY, "catalog",
                ARG_FROM, "14:00",
                ARG_TO, "14:45"))));

        var state = executor.execute(plan, budget(10_000, 20, Duration.ofMillis(30)));

        assertThat(state.statuses())
                .containsEntry("traces", NodeStatus.SKIPPED)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.SKIPPED);
        assertThat(state.errors())
                .containsEntry("traces", "budget exhausted")
                .containsEntry(ANALYZE_EVIDENCE, "budget exhausted");
        assertThat(state.result("traces")).isNull();
    }

    private static Lg4jPlanExecutor executor(RecordingToolExecutor toolExecutor) {
        return executor(toolExecutor, 4);
    }

    private static Lg4jPlanExecutor executor(Lg4jToolExecutor toolExecutor, int maxConcurrency) {
        var tools = new Lg4jTools();
        return new Lg4jPlanExecutor(toolExecutor, new Lg4jEvidenceAnalysisNode(tools), maxConcurrency);
    }

    private static Plan fullEvidencePlan() {
        return new Plan(List.of(
                node("metrics", QUERY_PROMETHEUS, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("comparison", COMPARE_PERIODS, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("logs", QUERY_LOKI, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "error timeout",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("signature", FIND_LOG_SIGNATURE, List.of(ref(ARG_LOGS, "logs")), "logs"),
                node("traces", QUERY_TEMPO, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "catalog",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("deployments", GET_DEPLOYMENTS, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("configs", GET_CONFIG_CHANGES, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45"))));
    }

    private static PlanNode node(String id, String tool, List<ArgumentBinding> arguments, String... deps) {
        return new PlanNode(id, tool, arguments, List.of(deps));
    }

    private static List<ArgumentBinding> literals(String... namesAndValues) {
        var bindings = new ArrayList<ArgumentBinding>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            bindings.add(lit(namesAndValues[i], namesAndValues[i + 1]));
        }
        return bindings;
    }

    private static ArgumentBinding lit(String name, String value) {
        return new ArgumentBinding(name, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding ref(String name, String sourceNodeId) {
        return new ArgumentBinding(name, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }

    private static Budget budget(long maxTokens, long maxToolCalls) {
        return budget(maxTokens, maxToolCalls, Duration.ofMinutes(1));
    }

    private static Budget budget(long maxTokens, long maxToolCalls, Duration maxWallClock) {
        return new Budget(
                new BudgetLimits(maxTokens, maxToolCalls, maxWallClock, new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static final class RecordingToolExecutor extends Lg4jToolExecutor {

        private final List<ToolCall> calls = new ArrayList<>();
        private final String failingTool;

        private RecordingToolExecutor() {
            this(null);
        }

        private RecordingToolExecutor(String failingTool) {
            super(new Lg4jTools());
            this.failingTool = failingTool;
        }

        @Override
        ToolExecutionResult execute(String name, Map<String, Object> args) {
            var safeArgs = args == null ? Map.<String, Object>of() : new HashMap<>(args);
            calls.add(new ToolCall(name, safeArgs));
            if (name.equals(failingTool)) {
                throw new IllegalStateException("planned failure: " + name);
            }
            return super.execute(name, safeArgs);
        }

        private Map<String, Object> argsFor(String nodeId) {
            return switch (nodeId) {
                case "comparison", "baselineComparison", "recentComparison" -> callsTo(COMPARE_PERIODS).stream()
                        .filter(call -> nodeId.equals("comparison")
                                || "13:00".equals(call.args().get(ARG_BASELINE_FROM))
                                && "baselineComparison".equals(nodeId)
                                || "14:00".equals(call.args().get(ARG_BASELINE_FROM))
                                && "recentComparison".equals(nodeId))
                        .map(ToolCall::args)
                        .findFirst()
                        .orElseThrow();
                case "signature" -> callsTo(FIND_LOG_SIGNATURE).getFirst().args();
                default -> throw new IllegalArgumentException("unknown recorded node: " + nodeId);
            };
        }

        private List<ToolCall> callsTo(String tool) {
            return calls.stream()
                    .filter(call -> call.tool().equals(tool))
                    .toList();
        }
    }

    private record ToolCall(String tool, Map<String, Object> args) {
    }

    private static final class SlowToolExecutor extends Lg4jToolExecutor {

        private final Duration delay;

        private SlowToolExecutor(Duration delay) {
            super(new Lg4jTools());
            this.delay = delay;
        }

        @Override
        ToolExecutionResult execute(String name, Map<String, Object> args) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return super.execute(name, args);
        }
    }
}
