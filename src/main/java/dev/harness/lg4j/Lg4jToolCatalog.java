package dev.harness.lg4j;

import org.springframework.stereotype.Component;

@Component
class Lg4jToolCatalog {

    String plannerCatalog() {
        return """
                query_prometheus(service, metric, from, to) -> PrometheusQueryResult role=EVIDENCE
                query_loki(service, query, from, to) -> LokiQueryResult role=EVIDENCE
                query_tempo(service, query, from, to) -> TempoQueryResult role=EVIDENCE
                get_deployments(service, from, to) -> List<DeploymentEvent> role=EVIDENCE
                get_config_changes(service, from, to) -> List<ConfigChange> role=EVIDENCE
                compare_periods(metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo) -> PeriodComparison role=ANALYSIS
                find_log_signature(logs) -> LogSignature role=ANALYSIS
                assemble_evidence(metricComparison, logSignature, traces, deployments, configChanges) -> EvidenceBundle role=ANALYSIS
                correlate(evidence) -> CorrelationResult role=ANALYSIS
                test_hypothesis(hypothesis, evidence) -> HypothesisAssessment role=HYPOTHESIS_TEST
                build_incident_report(incident, hypothesisAssessment, evidence) -> IncidentReport role=FINAL_SYNTHESIS
                """;
    }
}
