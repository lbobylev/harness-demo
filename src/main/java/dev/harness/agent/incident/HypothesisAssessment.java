package dev.harness.agent.incident;

import java.util.List;

public record HypothesisAssessment(
        String hypothesis,
        String strength,
        double confidence,
        List<String> evidenceFor,
        List<String> evidenceAgainst,
        List<String> missingEvidence,
        String decision,
        List<String> evidenceIds
) {
    public HypothesisAssessment {
        evidenceFor = evidenceFor == null ? List.of() : List.copyOf(evidenceFor);
        evidenceAgainst = evidenceAgainst == null ? List.of() : List.copyOf(evidenceAgainst);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
