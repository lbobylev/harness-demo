package dev.harness.agent.incident;

import java.util.List;

public record CorrelationResult(
        List<String> timeline,
        List<String> correlations,
        List<String> contradictions,
        List<String> evidenceIds
) {
    public CorrelationResult {
        timeline = timeline == null ? List.of() : List.copyOf(timeline);
        correlations = correlations == null ? List.of() : List.copyOf(correlations);
        contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
