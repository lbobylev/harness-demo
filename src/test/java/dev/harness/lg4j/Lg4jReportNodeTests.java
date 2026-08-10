package dev.harness.lg4j;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.IncidentReport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Lg4jReportNodeTests {

    private final Lg4jReportNode reportNode = new Lg4jReportNode(new Lg4jTools());

    @Test
    void buildsReportFromIncidentAnalysis() {
        var result = reportNode.build(new Lg4jRunState(Map.of(
                Lg4jRunState.GOAL, "checkout 5xx after deployment",
                Lg4jRunState.INCIDENT_ANALYSIS, analysis())));

        assertThat(result.get(Lg4jRunState.INCIDENT_REPORT))
                .isInstanceOf(IncidentReport.class);
    }

    private static Lg4jIncidentAnalysis analysis() {
        var correlation = new CorrelationResult(
                List.of("14:35 trace trace-1 spent 1800ms in checkout"),
                List.of("failed or slow trace points to catalog-service"),
                List.of("database degradation was not observed"),
                List.of("trace-1"));
        var assessment = new HypothesisAssessment(
                "catalog-service degradation caused checkout-service 5xx through downstream timeouts",
                "STRONG",
                0.89,
                List.of("slow or failed traces point to catalog-service"),
                List.of("checkout deployment is time-correlated but weaker"),
                List.of(),
                "SUPPORTED",
                List.of("trace-1"));
        return new Lg4jIncidentAnalysis(new EvidenceBundle(null, null, null, null, null, List.of("trace-1")),
                correlation, assessment);
    }
}
