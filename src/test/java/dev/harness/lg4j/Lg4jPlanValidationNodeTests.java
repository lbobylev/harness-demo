package dev.harness.lg4j;

import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_METRIC_SERIES;
import static dev.harness.agent.tools.IncidentInvestigationTools.COMPARE_PERIODS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_PROMETHEUS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_TEMPO;

class Lg4jPlanValidationNodeTests {

    private final Lg4jPlanValidationNode validationNode = new Lg4jPlanValidationNode();

    @Test
    void rejectsRuntimeTailNodesFromPlannerOutput() {
        var result = validationNode.validate(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN,
                new Plan(List.of(new PlanNode("report", "build_incident_report", List.of()))))));

        assertThat(result)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.FAILED_VALIDATION)
                .containsEntry(Lg4jRunState.ERROR, "unknown evidence tool: build_incident_report");
    }

    @Test
    void rejectsRawTerminalEvidenceNodes() {
        var result = validationNode.validate(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN,
                new Plan(List.of(new PlanNode("metrics", "query_prometheus", List.of()))))));

        assertThat(result)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.FAILED_VALIDATION)
                .containsEntry(Lg4jRunState.ERROR,
                        "terminal evidence node must produce analysis-ready evidence: metrics");
    }

    @Test
    void acceptsTerminalToolsFromCatalog() {
        var result = validationNode.validate(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN,
                new Plan(List.of(new PlanNode("traces", "query_tempo", List.of()))))));

        assertThat(result).isEmpty();
    }

    @Test
    void acceptsIntermediatePlannerNodeWhenConsumedByTerminalNode() {
        var result = validationNode.validate(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN,
                new Plan(List.of(
                        new PlanNode("metrics", QUERY_PROMETHEUS, List.of()),
                        new PlanNode("comparison", COMPARE_PERIODS,
                                List.of(ref(ARG_METRIC_SERIES, "metrics")), List.of("metrics")))))));

        assertThat(result).isEmpty();
    }

    @Test
    void rejectsDuplicatePlannerNodeIds() {
        var result = validate(new Plan(List.of(
                new PlanNode("traces", QUERY_TEMPO, List.of()),
                new PlanNode("traces", QUERY_TEMPO, List.of()))));

        assertValidationError(result, "duplicate plan node id: traces");
    }

    @Test
    void rejectsUnknownPlannerDependency() {
        var result = validate(new Plan(List.of(
                new PlanNode("comparison", COMPARE_PERIODS, List.of("missing")))));

        assertValidationError(result, "unknown dependency 'missing' in node 'comparison'");
    }

    @Test
    void rejectsPlannerDependencyCycles() {
        var result = validate(new Plan(List.of(
                new PlanNode("metrics", QUERY_PROMETHEUS, List.of("comparison")),
                new PlanNode("comparison", COMPARE_PERIODS, List.of("metrics")))));

        assertValidationError(result, "plan contains dependency cycle at node: metrics");
    }

    @Test
    void rejectsBlankNodeResultSource() {
        var result = validate(new Plan(List.of(
                new PlanNode("comparison", COMPARE_PERIODS,
                        List.of(ref(ARG_METRIC_SERIES, "")), List.of()))));

        assertValidationError(result, "NODE_RESULT sourceNodeId must not be blank in node comparison");
    }

    @Test
    void rejectsUnknownNodeResultSource() {
        var result = validate(new Plan(List.of(
                new PlanNode("comparison", COMPARE_PERIODS,
                        List.of(ref(ARG_METRIC_SERIES, "missing")), List.of()))));

        assertValidationError(result, "unknown NODE_RESULT source 'missing' in node 'comparison'");
    }

    @Test
    void rejectsNodeResultSourceMissingFromDeps() {
        var result = validate(new Plan(List.of(
                new PlanNode("metrics", QUERY_PROMETHEUS, List.of()),
                new PlanNode("comparison", COMPARE_PERIODS,
                        List.of(ref(ARG_METRIC_SERIES, "metrics")), List.of()))));

        assertValidationError(result, "NODE_RESULT source 'metrics' must be listed in deps for node 'comparison'");
    }

    @Test
    void rejectsEmptyPlannerPlan() {
        var result = validate(new Plan(List.of()));

        assertValidationError(result, "plan must contain at least one node");
    }

    @Test
    void exposesTerminalMetadataInPromptCatalog() {
        assertThat(Lg4jToolSpecs.promptCatalog())
                .contains("query_prometheus(service, metric, from, to) -> PrometheusQueryResult role=EVIDENCE terminal=false")
                .contains("query_tempo(service, query, from, to) -> TempoQueryResult role=EVIDENCE terminal=true")
                .contains("compare_periods(metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo) -> PeriodComparison role=ANALYSIS terminal=true");
    }

    private Map<String, Object> validate(Plan plan) {
        return validationNode.validate(new Lg4jRunState(Map.of(Lg4jRunState.PLAN, plan)));
    }

    private static ArgumentBinding ref(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }

    private static void assertValidationError(Map<String, Object> result, String error) {
        assertThat(result)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.FAILED_VALIDATION)
                .containsEntry(Lg4jRunState.ERROR, error);
    }
}
