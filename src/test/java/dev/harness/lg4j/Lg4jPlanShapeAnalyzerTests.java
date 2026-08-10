package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Lg4jPlanShapeAnalyzerTests {

    private final Lg4jPlanShapeAnalyzer analyzer = new Lg4jPlanShapeAnalyzer();

    @Test
    void analyzesLinearEvidenceBranchesAndTail() {
        var shape = analyzer.analyze(new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("compare", "compare_periods", "metrics"),
                node("logs", "query_loki"),
                node("signature", "find_log_signature", "logs"),
                node("assemble", "assemble_evidence", "compare", "signature"),
                node("correlate", "correlate", "assemble"),
                node("test", "test_hypothesis", "correlate"),
                node("report", "build_incident_report", "test", "correlate")
        )));

        assertThat(shape.branches()).containsExactly(
                List.of("metrics", "compare"),
                List.of("logs", "signature"));
        assertThat(shape.tail()).containsExactly("assemble", "correlate", "test", "report");
    }

    @Test
    void rejectsBranchFanInBeforeAssembleEvidence() {
        var plan = new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("logs", "query_loki"),
                node("mixed", "compare_periods", "metrics", "logs"),
                node("assemble", "assemble_evidence", "mixed"),
                node("correlate", "correlate", "assemble"),
                node("test", "test_hypothesis", "correlate"),
                node("report", "build_incident_report", "test")
        ));

        assertThatThrownBy(() -> analyzer.analyze(plan))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple dependencies");
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }
}
