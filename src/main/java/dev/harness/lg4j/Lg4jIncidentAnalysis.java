package dev.harness.lg4j;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.HypothesisAssessment;

import java.io.Serializable;

record Lg4jIncidentAnalysis(
        EvidenceBundle evidence,
        CorrelationResult correlation,
        HypothesisAssessment hypothesisAssessment
) implements Serializable {
}
