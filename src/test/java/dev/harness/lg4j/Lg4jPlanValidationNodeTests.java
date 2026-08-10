package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    void exposesTerminalMetadataInPromptCatalog() {
        assertThat(Lg4jToolSpecs.promptCatalog())
                .contains("query_prometheus(service, metric, from, to) -> PrometheusQueryResult role=EVIDENCE terminal=false")
                .contains("query_tempo(service, query, from, to) -> TempoQueryResult role=EVIDENCE terminal=true")
                .contains("compare_periods(metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo) -> PeriodComparison role=ANALYSIS terminal=true");
    }
}
