package dev.harness.agent.incident;

import java.io.Serializable;
import java.util.List;

public record EvidenceBundle(
        List<PeriodComparison> metricComparisons,
        List<LogSignature> logSignatures,
        List<TempoQueryResult> traces,
        List<DeploymentEvent> deployments,
        List<ConfigChange> configChanges,
        List<String> evidenceIds
) implements Serializable {
    public EvidenceBundle {
        metricComparisons = metricComparisons == null ? List.of() : List.copyOf(metricComparisons);
        logSignatures = logSignatures == null ? List.of() : List.copyOf(logSignatures);
        traces = traces == null ? List.of() : List.copyOf(traces);
        deployments = deployments == null ? List.of() : List.copyOf(deployments);
        configChanges = configChanges == null ? List.of() : List.copyOf(configChanges);
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
