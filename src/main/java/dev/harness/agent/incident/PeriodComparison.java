package dev.harness.agent.incident;

import java.util.List;

public record PeriodComparison(
        String metricId,
        double baselineAverage,
        double incidentAverage,
        double delta,
        String change,
        List<String> evidenceIds
) {
    public PeriodComparison {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
