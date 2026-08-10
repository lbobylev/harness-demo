package dev.harness.agent.incident;

import java.io.Serializable;
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
) implements Serializable {
    public HypothesisAssessment {
        evidenceFor = evidenceFor == null ? List.of() : List.copyOf(evidenceFor);
        evidenceAgainst = evidenceAgainst == null ? List.of() : List.copyOf(evidenceAgainst);
        missingEvidence = missingEvidence == null ? List.of() : List.copyOf(missingEvidence);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
