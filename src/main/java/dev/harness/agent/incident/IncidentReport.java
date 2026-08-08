package dev.harness.agent.incident;

import dev.harness.agent.verification.FinalReport;

import java.util.List;

public record IncidentReport(
        String rootCause,
        double confidence,
        List<String> timeline,
        List<String> evidence,
        List<String> rejectedHypotheses,
        String recommendedAction
) implements FinalReport {
    public IncidentReport {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        rejectedHypotheses = rejectedHypotheses == null ? List.of() : List.copyOf(rejectedHypotheses);
    }

    @Override
    public String reportText() {
        return "Root cause: %s\nConfidence: %.2f\nTimeline: %s\nEvidence: %s\nRejected hypotheses: %s\nRecommended action: %s"
                .formatted(rootCause, confidence, timeline, evidence, rejectedHypotheses, recommendedAction);
    }
}
