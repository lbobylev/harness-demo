package dev.harness.lg4j;

import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class Lg4jVerificationNode {

    static final String LOW_CONFIDENCE = "lowConfidence";
    static final String CONFIDENCE = "confidence";
    static final String THRESHOLD = "threshold";

    private final double minConfidence;

    Lg4jVerificationNode(@Value("${harness.verification.min-confidence:0.75}") double minConfidence) {
        this.minConfidence = minConfidence;
    }

    Map<String, Object> verify(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }

        var plan = state.plan().orElse(null);
        var report = state.incidentReport().orElse(null);
        var verdict = verifyPlan(plan, report, minConfidence);
        if (isLowConfidence(verdict)) {
            return Map.of(Lg4jRunState.VERDICT, verdict);
        }
        if (!verdict.passed()) {
            return Map.of(
                    Lg4jRunState.STATUS, RunStatus.FAILED_VERIFICATION,
                    Lg4jRunState.ERROR_CLASS, ErrorClass.VALIDATION,
                    Lg4jRunState.ERROR, verdict.reason(),
                    Lg4jRunState.VERDICT, verdict
            );
        }

        return Map.of(
                Lg4jRunState.STATUS, RunStatus.SUCCEEDED,
                Lg4jRunState.REPORT, report.reportText(),
                Lg4jRunState.VERDICT, verdict
        );
    }

    private static VerificationVerdict verifyPlan(Plan plan, IncidentReport report, double minConfidence) {
        if (plan == null) {
            return VerificationVerdict.failed("plan must not be null");
        }
        if (plan.nodes().stream().anyMatch(node -> node != null && (node.isFailed() || node.isSkipped()))) {
            return VerificationVerdict.failed("plan contains failed or skipped nodes");
        }
        if (report == null) {
            return VerificationVerdict.failed("incident report must be present");
        }
        if (report.rootCause() == null || report.rootCause().isBlank()) {
            return VerificationVerdict.failed("incident report rootCause must not be blank");
        }
        if (report.confidence() < minConfidence) {
            return VerificationVerdict.failed("incident report confidence below threshold", Map.of(
                    LOW_CONFIDENCE, true,
                    CONFIDENCE, report.confidence(),
                    THRESHOLD, minConfidence));
        }
        if (report.timeline().size() < 3) {
            return VerificationVerdict.failed("incident report timeline must contain at least 3 entries");
        }
        if (report.evidence().size() < 3) {
            return VerificationVerdict.failed("incident report evidence must contain at least 3 items");
        }
        if (report.rejectedHypotheses().isEmpty()) {
            return VerificationVerdict.failed("incident report must include rejected hypotheses");
        }
        if (report.recommendedAction() == null || report.recommendedAction().isBlank()) {
            return VerificationVerdict.failed("incident report recommendedAction must not be blank");
        }

        return VerificationVerdict.pass();
    }

    static boolean isLowConfidence(VerificationVerdict verdict) {
        return verdict != null && Boolean.TRUE.equals(verdict.details().get(LOW_CONFIDENCE));
    }
}
