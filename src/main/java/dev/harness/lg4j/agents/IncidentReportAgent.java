package dev.harness.lg4j.agents;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.HypothesisAssessment;
import dev.harness.agent.incident.IncidentReport;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentReportAgent {

    public IncidentReport build(String incident, HypothesisAssessment hypothesisAssessment, CorrelationResult evidence) {
        return new IncidentReport(
                "catalog-service degradation caused checkout-service 5xx through downstream timeouts",
                0.89,
                evidence == null ? new EvidenceCorrelationAgent().correlate(null).timeline() : evidence.timeline(),
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("checkout deployment regression", "database degradation"),
                "Mitigate catalog-service degradation or temporarily degrade checkout catalog enrichment path");
    }
}
