package dev.harness.agent.tools;

import dev.harness.agent.incident.IncidentData;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCatalogTests {

    @Test
    void exposesSpringAiToolDefinitions() {
        ToolCatalog catalog = new ToolCatalog(new IncidentInvestigationTools(new IncidentData()));

        assertThat(catalog.toolNames()).containsExactlyInAnyOrder(
                "query_prometheus",
                "query_loki",
                "query_tempo",
                "get_deployments",
                "get_config_changes",
                "compare_periods",
                "find_log_signature",
                "assemble_evidence",
                "correlate",
                "test_hypothesis",
                "build_incident_report"
        );
        assertThat(catalog.definitions())
                .allSatisfy(definition -> {
                    assertThat(definition.description()).isNotBlank();
                    assertThat(definition.inputSchema()).isNotBlank();
                });
        assertThat(catalog.finalSynthesisTool())
                .get()
                .extracting(ToolDefinitionView::name)
                .isEqualTo("build_incident_report");
        assertThat(catalog.roleOf("query_prometheus")).isEqualTo(ToolRole.EVIDENCE);
        assertThat(catalog.roleOf("compare_periods")).isEqualTo(ToolRole.ANALYSIS);
        assertThat(catalog.roleOf("test_hypothesis")).isEqualTo(ToolRole.HYPOTHESIS_TEST);
        assertThat(catalog.requiredArgumentNames("build_incident_report"))
                .containsExactlyInAnyOrder("incident", "hypothesisAssessment", "evidence");
        assertThat(catalog.argumentNames("query_prometheus"))
                .containsExactlyInAnyOrder("service", "metric", "from", "to");
    }
}
