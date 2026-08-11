package dev.harness.lg4j;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class Lg4jReportNode {

    private final IncidentReportAgent incidentReportAgent;

    Lg4jReportNode(IncidentReportAgent incidentReportAgent) {
        this.incidentReportAgent = incidentReportAgent;
    }

    Map<String, Object> build(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }

        var analysis = state.incidentAnalysis().orElse(null);
        if (analysis == null) {
            return failure("incident analysis must be present");
        }

        try {
            var report = incidentReportAgent.build(
                    state.goal().orElse(""),
                    analysis.hypothesisAssessment(),
                    analysis.correlation());
            return Map.of(Lg4jRunState.INCIDENT_REPORT, report);
        } catch (Exception exception) {
            return failure(exception.getMessage() == null ? "report generation failed" : exception.getMessage());
        }
    }

    private Map<String, Object> failure(String error) {
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                Lg4jRunState.ERROR, error);
    }
}
