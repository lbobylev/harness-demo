package dev.harness.lg4j.execution;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.execution.AgentResponse;
import dev.harness.agent.execution.AgentSpent;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.lg4j.agents.EvidenceCorrelationAgent;
import dev.harness.lg4j.agents.HypothesisAssessmentAgent;
import dev.harness.lg4j.agents.Lg4jAgentExecutor;
import dev.harness.lg4j.incident.Lg4jIncidentAnalysis;
import dev.harness.lg4j.nodes.Lg4jEvidenceAnalysisNode;
import dev.harness.lg4j.state.Lg4jPlanExecutionState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_BASELINE_FROM;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_BASELINE_TO;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_FROM;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_INCIDENT_FROM;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_INCIDENT_TO;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_LOGS;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_METRIC;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_METRIC_SERIES;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_QUERY;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_SERVICE;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.ARG_TO;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.CONFIG_CHANGES_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.DEPLOYMENTS_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.LOGS_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.LOG_SIGNATURE_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.METRICS_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.METRIC_COMPARISON_AGENT;
import static dev.harness.lg4j.agents.Lg4jAgentSpecs.TRACES_AGENT;
import static org.assertj.core.api.Assertions.assertThat;

class Lg4jPlanExecutorTests {

    private static final String ANALYZE_EVIDENCE = Lg4jPlanExecutionState.ANALYZE_EVIDENCE;

    @Test
    void doesNotExecuteMoreReadyNodesThanRemainingAgentInvocationBudget() {
        var budget = budget(100, 1);
        var executor = new Lg4jPlanExecutor(
                agentExecutor(),
                evidenceAnalysisNode(),
                4);
        var plan = new Plan(List.of(
                new PlanNode("metrics", METRICS_AGENT, List.of()),
                new PlanNode("logs", LOGS_AGENT, List.of())));

        var state = executor.execute(plan, budget);

        assertThat(budget.snapshot().agentInvocationsUsed()).isEqualTo(1L);
        assertThat(state.statuses().entrySet())
                .filteredOn(entry -> List.of("metrics", "logs").contains(entry.getKey()))
                .extracting(Map.Entry::getValue)
                .containsExactlyInAnyOrder(NodeStatus.DONE, NodeStatus.SKIPPED);
        assertThat(state.errors()).containsValue("budget exhausted");
    }

    @Test
    void executesSingleTerminalPlannerNode() {
        var agentExecutor = new RecordingAgentExecutor();
        var executor = executor(agentExecutor);
        var plan = new Plan(List.of(node("traces", TRACES_AGENT, literals(
                ARG_SERVICE, "checkout-service",
                ARG_QUERY, "catalog",
                ARG_FROM, "14:00",
                ARG_TO, "14:45"))));

        var state = executor.execute(plan, budget(10_000, 20));

        assertThat(state.errors()).isEmpty();
        assertThat(state.statuses())
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry(ANALYZE_EVIDENCE, NodeStatus.DONE);
        assertThat(agentExecutor.callsTo(TRACES_AGENT)).hasSize(1);
        assertThat(state.result(ANALYZE_EVIDENCE)).isInstanceOf(Lg4jIncidentAnalysis.class);
    }

    @Test
    void chargesAgentSpentToRunBudget() {
        var agentExecutor = new SpentAgentExecutor(new AiUsage("agent-model", 7, 3, 10));
        var executor = executor(agentExecutor);
        var budget = budget(10_000, 20);
        var plan = new Plan(List.of(node("traces", TRACES_AGENT, literals(
                ARG_SERVICE, "checkout-service",
                ARG_QUERY, "catalog",
                ARG_FROM, "14:00",
                ARG_TO, "14:45"))));

        executor.execute(plan, budget);

        assertThat(budget.snapshot().tokensUsed()).isEqualTo(10L);
        assertThat(budget.snapshot().agentInvocationsUsed()).isEqualTo(1L);
    }

    @Test
    void executesPlannerDagAndPassesNodeResultsToDownstreamAgents() {
        var agentExecutor = new RecordingAgentExecutor();
        var executor = executor(agentExecutor);
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

        assertThat(agentExecutor.argsFor("comparison").get(ARG_METRIC_SERIES))
                .isEqualTo(state.result("metrics"));
        assertThat(agentExecutor.argsFor("signature").get(ARG_LOGS))
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
        var agentExecutor = new RecordingAgentExecutor();
        var executor = executor(agentExecutor);
        var plan = new Plan(List.of(
                node("metrics", METRICS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("baselineComparison", METRIC_COMPARISON_AGENT, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("recentComparison", METRIC_COMPARISON_AGENT, List.of(
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
        assertThat(agentExecutor.callsTo(METRICS_AGENT)).hasSize(1);
        assertThat(agentExecutor.argsFor("baselineComparison").get(ARG_METRIC_SERIES))
                .isEqualTo(state.result("metrics"));
        assertThat(agentExecutor.argsFor("recentComparison").get(ARG_METRIC_SERIES))
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
        var agentExecutor = new RecordingAgentExecutor(METRICS_AGENT);
        var executor = executor(agentExecutor);
        var plan = new Plan(List.of(
                node("metrics", METRICS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("comparison", METRIC_COMPARISON_AGENT, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("logs", LOGS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "error timeout",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("signature", LOG_SIGNATURE_AGENT, List.of(ref(ARG_LOGS, "logs")), "logs"),
                node("traces", TRACES_AGENT, literals(
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
                .containsEntry("metrics", "planned failure: MetricsAgent")
                .containsEntry("comparison", "dependency failed")
                .containsEntry(ANALYZE_EVIDENCE, "dependency failed");
        assertThat(agentExecutor.callsTo(METRIC_COMPARISON_AGENT)).isEmpty();
        assertThat(agentExecutor.callsTo(LOGS_AGENT)).hasSize(1);
        assertThat(agentExecutor.callsTo(LOG_SIGNATURE_AGENT)).hasSize(1);
        assertThat(agentExecutor.callsTo(TRACES_AGENT)).hasSize(1);
    }

    @Test
    void skipsAgentInvocationWhenWallClockBudgetExpires() {
        var executor = new Lg4jPlanExecutor(
                new SlowAgentExecutor(Duration.ofMillis(200)),
                evidenceAnalysisNode(),
                1);
        var plan = new Plan(List.of(node("traces", TRACES_AGENT, literals(
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

    private static Lg4jPlanExecutor executor(RecordingAgentExecutor agentExecutor) {
        return executor(agentExecutor, 4);
    }

    private static Lg4jPlanExecutor executor(Lg4jAgentExecutor agentExecutor, int maxConcurrency) {
        return new Lg4jPlanExecutor(agentExecutor, evidenceAnalysisNode(), maxConcurrency);
    }

    private static Plan fullEvidencePlan() {
        return new Plan(List.of(
                node("metrics", METRICS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_METRIC, "5xx_rate",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("comparison", METRIC_COMPARISON_AGENT, List.of(
                        ref(ARG_METRIC_SERIES, "metrics"),
                        lit(ARG_BASELINE_FROM, "13:00"),
                        lit(ARG_BASELINE_TO, "13:45"),
                        lit(ARG_INCIDENT_FROM, "14:00"),
                        lit(ARG_INCIDENT_TO, "14:45")), "metrics"),
                node("logs", LOGS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "error timeout",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("signature", LOG_SIGNATURE_AGENT, List.of(ref(ARG_LOGS, "logs")), "logs"),
                node("traces", TRACES_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_QUERY, "catalog",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("deployments", DEPLOYMENTS_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45")),
                node("configs", CONFIG_CHANGES_AGENT, literals(
                        ARG_SERVICE, "checkout-service",
                        ARG_FROM, "14:00",
                        ARG_TO, "14:45"))));
    }

    private static PlanNode node(String id, String agent, List<ArgumentBinding> arguments, String... deps) {
        return new PlanNode(id, agent, arguments, List.of(deps));
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

    private static Budget budget(long maxTokens, long maxAgentInvocations) {
        return budget(maxTokens, maxAgentInvocations, Duration.ofMinutes(1));
    }

    private static Budget budget(long maxTokens, long maxAgentInvocations, Duration maxWallClock) {
        return new Budget(
                new BudgetLimits(maxTokens, maxAgentInvocations, maxWallClock, new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private static Lg4jEvidenceAnalysisNode evidenceAnalysisNode() {
        return new Lg4jEvidenceAnalysisNode(new EvidenceCorrelationAgent(), new HypothesisAssessmentAgent());
    }

    private static Lg4jAgentExecutor agentExecutor() {
        return Lg4jAgentExecutor.defaults();
    }

    private static class RecordingAgentExecutor extends Lg4jAgentExecutor {

        private final List<AgentInvocation> calls = Collections.synchronizedList(new ArrayList<>());
        private final String failingAgent;

        private RecordingAgentExecutor() {
            this(null);
        }

        private RecordingAgentExecutor(String failingAgent) {
            this.failingAgent = failingAgent;
        }

        @Override
        public AgentResponse execute(String name, Map<String, Object> args) {
            var safeArgs = args == null ? Map.<String, Object>of() : new HashMap<>(args);
            calls.add(new AgentInvocation(name, safeArgs));
            if (name.equals(failingAgent)) {
                throw new IllegalStateException("planned failure: " + name);
            }
            return super.execute(name, safeArgs);
        }

        private Map<String, Object> argsFor(String nodeId) {
            return switch (nodeId) {
                case "comparison", "baselineComparison", "recentComparison" -> callsTo(METRIC_COMPARISON_AGENT).stream()
                        .filter(call -> nodeId.equals("comparison")
                                || "13:00".equals(call.args().get(ARG_BASELINE_FROM))
                                && "baselineComparison".equals(nodeId)
                                || "14:00".equals(call.args().get(ARG_BASELINE_FROM))
                                && "recentComparison".equals(nodeId))
                        .map(AgentInvocation::args)
                        .findFirst()
                        .orElseThrow();
                case "signature" -> callsTo(LOG_SIGNATURE_AGENT).getFirst().args();
                default -> throw new IllegalArgumentException("unknown recorded node: " + nodeId);
            };
        }

        private List<AgentInvocation> callsTo(String agent) {
            return new ArrayList<>(calls).stream()
                    .filter(call -> call.agent().equals(agent))
                    .toList();
        }
    }

    private record AgentInvocation(String agent, Map<String, Object> args) {
    }

    private static final class SlowAgentExecutor extends RecordingAgentExecutor {

        private final Duration delay;

        private SlowAgentExecutor(Duration delay) {
            this.delay = delay;
        }

        @Override
        public AgentResponse execute(String name, Map<String, Object> args) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return super.execute(name, args);
        }
    }

    private static final class SpentAgentExecutor extends RecordingAgentExecutor {

        private final AiUsage usage;

        private SpentAgentExecutor(AiUsage usage) {
            this.usage = usage;
        }

        @Override
        public AgentResponse execute(String name, Map<String, Object> args) {
            var response = super.execute(name, args);
            return new AgentResponse(response.value(), AgentSpent.of(usage));
        }
    }
}
