package dev.harness.lg4j;

import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Lg4jVerificationNodeTests {

    @Test
    void lowConfidenceProducesNonTerminalVerdictForReplanning() {
        var update = new Lg4jVerificationNode(0.75).verify(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN, new Plan(List.of()),
                Lg4jRunState.INCIDENT_REPORT, report(0.62))));

        assertThat(update).doesNotContainKey(Lg4jRunState.STATUS);
        assertThat(update.get(Lg4jRunState.VERDICT))
                .isInstanceOfSatisfying(VerificationVerdict.class, verdict -> {
                    assertThat(verdict.passed()).isFalse();
                    assertThat(verdict.reason()).isEqualTo("incident report confidence below threshold");
                    assertThat(verdict.details())
                            .containsEntry(Lg4jVerificationNode.LOW_CONFIDENCE, true)
                            .containsEntry(Lg4jVerificationNode.CONFIDENCE, 0.62)
                            .containsEntry(Lg4jVerificationNode.THRESHOLD, 0.75);
                });
    }

    @Test
    void invalidReportFailureRemainsTerminal() {
        var update = new Lg4jVerificationNode(0.75).verify(new Lg4jRunState(Map.of(
                Lg4jRunState.PLAN, new Plan(List.of()),
                Lg4jRunState.INCIDENT_REPORT, new IncidentReport(
                        "",
                        0.90,
                        List.of("one", "two", "three"),
                        List.of("e1", "e2", "e3"),
                        List.of("database"),
                        "mitigate"))));

        assertThat(update)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.FAILED_VERIFICATION)
                .containsEntry(Lg4jRunState.ERROR, "incident report rootCause must not be blank");
    }

    private static IncidentReport report(double confidence) {
        return new IncidentReport(
                "catalog-service degradation",
                confidence,
                List.of("one", "two", "three"),
                List.of("e1", "e2", "e3"),
                List.of("database degradation"),
                "mitigate catalog-service");
    }
}
