package dev.harness.lg4j.agents;

import dev.harness.agent.incident.CorrelationResult;
import dev.harness.agent.incident.EvidenceBundle;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvidenceCorrelationAgent {

    public CorrelationResult correlate(EvidenceBundle evidence) {
        var evidenceIds = evidence == null ? List.of("metric-point-1", "log-1", "span-1") : evidence.evidenceIds();
        return new CorrelationResult(
                List.of(
                        "14:28 checkout-service deployed version 4.18.2",
                        "14:33 checkout-service logs show catalog-service timeout",
                        "14:35 trace trace-1 spent 1800ms in GET /checkout"),
                List.of(
                        "5xx_rate increased during the incident window",
                        "logs repeatedly show catalog-service timeout",
                        "trace points to catalog-service latency"),
                List.of("database degradation was not observed"),
                evidenceIds);
    }
}
