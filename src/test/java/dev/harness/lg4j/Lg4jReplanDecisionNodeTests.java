package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Lg4jReplanDecisionNodeTests {

    @Test
    void lowConfidenceWithBudgetAndReplansRemainingRequestsReplan() {
        var update = new Lg4jReplanDecisionNode(2).decide(lowConfidenceState(0), budget(100, 10));

        assertThat(update)
                .containsEntry(Lg4jRunState.NEEDS_REPLAN, true)
                .containsEntry(Lg4jRunState.REPLAN_COUNT, 1);
        assertThat((String) update.get(Lg4jRunState.FAILURE_CONTEXT))
                .contains("Previous attempt produced low confidence")
                .contains("Confidence: 0.62")
                .contains("Required: 0.75")
                .contains("Try a different investigation angle");
    }

    @Test
    void maxReplansExhaustedFinishesVerificationFailure() {
        var update = new Lg4jReplanDecisionNode(1).decide(lowConfidenceState(1), budget(100, 10));

        assertThat(update)
                .containsEntry(Lg4jRunState.NEEDS_REPLAN, false)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.FAILED_VERIFICATION)
                .containsEntry(Lg4jRunState.ERROR, "incident report confidence below threshold");
    }

    @Test
    void exhaustedBudgetStopsBeforeReplanning() {
        var budget = budget(1, 10);
        budget.chargeTokens(1, 0);

        var update = new Lg4jReplanDecisionNode(2).decide(lowConfidenceState(0), budget);

        assertThat(update)
                .containsEntry(Lg4jRunState.NEEDS_REPLAN, false)
                .containsEntry(Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED)
                .containsEntry(Lg4jRunState.ERROR, "budget exhausted before replanning");
    }

    @Test
    void nonLowConfidenceVerdictDoesNotRequestReplan() {
        var update = new Lg4jReplanDecisionNode(2).decide(new Lg4jRunState(Map.of(
                Lg4jRunState.VERDICT, VerificationVerdict.failed("invalid report"))), budget(100, 10));

        assertThat(update).containsEntry(Lg4jRunState.NEEDS_REPLAN, false);
    }

    @Test
    void terminalStateDoesNotReplanStaleLowConfidenceVerdict() {
        var update = new Lg4jReplanDecisionNode(2).decide(new Lg4jRunState(Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.VERDICT, VerificationVerdict.failed("incident report confidence below threshold", Map.of(
                        Lg4jVerificationNode.LOW_CONFIDENCE, true)))), budget(100, 10));

        assertThat(update).containsEntry(Lg4jRunState.NEEDS_REPLAN, false);
    }

    private static Lg4jRunState lowConfidenceState(int replanCount) {
        return new Lg4jRunState(Map.of(
                Lg4jRunState.REPLAN_COUNT, replanCount,
                Lg4jRunState.INCIDENT_REPORT, new IncidentReport(
                        "catalog-service degradation",
                        0.62,
                        List.of("one", "two", "three"),
                        List.of("e1", "e2", "e3"),
                        List.of("database degradation"),
                        "mitigate catalog-service"),
                Lg4jRunState.VERDICT, VerificationVerdict.failed("incident report confidence below threshold", Map.of(
                        Lg4jVerificationNode.LOW_CONFIDENCE, true,
                        Lg4jVerificationNode.CONFIDENCE, 0.62,
                        Lg4jVerificationNode.THRESHOLD, 0.75))));
    }

    private static Budget budget(long maxTokens, long maxAgentInvocations) {
        return new Budget(
                new BudgetLimits(maxTokens, maxAgentInvocations, Duration.ofMinutes(1), new BigDecimal("100.00")),
                new ModelPricing("test-model", BigDecimal.ZERO, BigDecimal.ZERO));
    }
}
