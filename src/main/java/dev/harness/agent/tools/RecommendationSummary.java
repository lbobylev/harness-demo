package dev.harness.agent.tools;

import dev.harness.agent.verification.FinalReport;

public record RecommendationSummary(String recommendation) implements FinalReport {

    @Override
    public String reportText() {
        return recommendation;
    }
}
