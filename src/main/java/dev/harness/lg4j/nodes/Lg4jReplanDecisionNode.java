package dev.harness.lg4j.nodes;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.lg4j.state.Lg4jRunState;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Lg4jReplanDecisionNode {

    private final int maxReplans;

    public Lg4jReplanDecisionNode(@Value("${harness.replanning.max-replans:2}") int maxReplans) {
        this.maxReplans = Math.max(0, maxReplans);
    }

    public Map<String, Object> decide(Lg4jRunState state, Budget budget) {
        if (state.terminal()) {
            return Map.of(Lg4jRunState.NEEDS_REPLAN, false);
        }
        if (!state.verdict().map(Lg4jVerificationNode::isLowConfidence).orElse(false)) {
            return Map.of(Lg4jRunState.NEEDS_REPLAN, false);
        }
        if (state.replanCount() >= maxReplans) {
            return Map.of(
                    Lg4jRunState.NEEDS_REPLAN, false,
                    Lg4jRunState.STATUS, RunStatus.FAILED_VERIFICATION,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.VALIDATION,
                    Lg4jRunState.ERROR, lowConfidenceReason(state));
        }
        if (budget == null || budget.exhausted() || budget.wallClockExhausted()) {
            return Map.of(
                    Lg4jRunState.NEEDS_REPLAN, false,
                    Lg4jRunState.STATUS, RunStatus.BUDGET_EXHAUSTED,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                    Lg4jRunState.ERROR, "budget exhausted before replanning");
        }

        return Map.of(
                Lg4jRunState.NEEDS_REPLAN, true,
                Lg4jRunState.REPLAN_COUNT, state.replanCount() + 1,
                Lg4jRunState.FAILURE_CONTEXT, failureContext(state));
    }

    private static String lowConfidenceReason(Lg4jRunState state) {
        return state.verdict().map(verdict -> verdict.reason()).orElse("incident report confidence below threshold");
    }

    private static String failureContext(Lg4jRunState state) {
        var report = state.incidentReport().orElse(null);
        var verdict = state.verdict().orElse(null);
        var details = verdict == null ? Map.<String, Object>of() : verdict.details();
        return """
                Previous attempt produced low confidence.

                Confidence: %s
                Required: %s
                Previous root cause: %s
                Previous evidence: %s
                Rejected hypotheses: %s

                Try a different investigation angle. Use a different or broader agent DAG.
                Avoid repeating the same evidence shape.
                """.formatted(
                details.getOrDefault(Lg4jVerificationNode.CONFIDENCE, "unknown"),
                details.getOrDefault(Lg4jVerificationNode.THRESHOLD, "unknown"),
                reportText(report, IncidentReport::rootCause),
                report == null ? "[]" : report.evidence(),
                report == null ? "[]" : report.rejectedHypotheses());
    }

    private static String reportText(IncidentReport report, java.util.function.Function<IncidentReport, String> getter) {
        if (report == null) {
            return "unknown";
        }
        var value = getter.apply(report);
        return value == null || value.isBlank() ? "unknown" : value;
    }
}
