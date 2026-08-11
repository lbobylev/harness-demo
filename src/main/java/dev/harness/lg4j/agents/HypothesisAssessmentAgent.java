package dev.harness.lg4j.agents;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.HypothesisAssessment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HypothesisAssessmentAgent {

    public HypothesisAssessment assess(String hypothesis, CorrelationResult evidence) {
        return new HypothesisAssessment(
                Lg4jAgentDefaults.text(hypothesis, "catalog-service latency caused checkout-service 5xx"),
                "STRONG",
                0.89,
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("checkout deployment is time-correlated but weaker"),
                List.of(),
                "SUPPORTED",
                evidence == null ? List.of("metric-point-1", "log-1", "span-1") : evidence.evidenceIds());
    }
}
