package dev.harness.lg4j;

import dev.harness.agent.incident.ConfigChange;
import dev.harness.agent.incident.DeploymentEvent;
import dev.harness.agent.incident.EvidenceBundle;
import dev.harness.agent.incident.LogSignature;
import dev.harness.agent.incident.PeriodComparison;
import dev.harness.agent.incident.TempoQueryResult;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
class Lg4jEvidenceAnalysisNode {

    private static final String DEFAULT_HYPOTHESIS = "catalog-service degradation caused checkout-service 5xx through downstream timeouts";
    private static final String DEPENDENCY_FAILED = "dependency failed";

    private final Lg4jTools tools;

    Lg4jEvidenceAnalysisNode(Lg4jTools tools) {
        this.tools = tools;
    }

    Map<String, Object> analyze(Plan plan, Lg4jPlanExecutionState state) {
        if (hasFailedOrSkippedGeneratedNode(plan, state)) {
            return stateUpdate(NodeStatus.SKIPPED, null, DEPENDENCY_FAILED);
        }

        try {
            var evidence = evidenceBundle(plan, state);
            var correlation = tools.correlate(evidence);
            var assessment = tools.testHypothesis(DEFAULT_HYPOTHESIS, correlation);
            var analysis = new Lg4jIncidentAnalysis(evidence, correlation, assessment);
            return stateUpdate(NodeStatus.DONE, analysis, null);
        } catch (Exception exception) {
            return stateUpdate(NodeStatus.FAILED, null, exception.getMessage());
        }
    }

    private boolean hasFailedOrSkippedGeneratedNode(Plan plan, Lg4jPlanExecutionState state) {
        return plan.nodes().stream()
                .anyMatch(node -> state.statuses().get(node.getId()) == NodeStatus.FAILED
                        || state.statuses().get(node.getId()) == NodeStatus.SKIPPED);
    }

    private EvidenceBundle evidenceBundle(Plan plan, Lg4jPlanExecutionState state) {
        var metricComparisons = new ArrayList<PeriodComparison>();
        var logSignatures = new ArrayList<LogSignature>();
        var traces = new ArrayList<TempoQueryResult>();
        var deployments = new ArrayList<DeploymentEvent>();
        var configChanges = new ArrayList<ConfigChange>();

        for (var terminal : Lg4jPlanDag.terminals(plan)) {
            collectEvidence(state.result(terminal.getId()), metricComparisons, logSignatures, traces, deployments, configChanges);
        }

        var evidenceIds = new ArrayList<String>();
        metricComparisons.forEach(comparison -> evidenceIds.addAll(comparison.evidenceIds()));
        logSignatures.forEach(signature -> evidenceIds.addAll(signature.evidenceIds()));
        traces.forEach(result -> result.spans().forEach(span -> evidenceIds.add(span.id())));
        deployments.forEach(deployment -> evidenceIds.add(deployment.id()));
        configChanges.forEach(change -> evidenceIds.add(change.id()));

        return new EvidenceBundle(metricComparisons, logSignatures, traces, deployments, configChanges,
                evidenceIds.stream().distinct().toList());
    }

    private void collectEvidence(
            Object value,
            List<PeriodComparison> metricComparisons,
            List<LogSignature> logSignatures,
            List<TempoQueryResult> traces,
            List<DeploymentEvent> deployments,
            List<ConfigChange> configChanges) {
        if (value instanceof PeriodComparison comparison) {
            metricComparisons.add(comparison);
            return;
        }
        if (value instanceof LogSignature signature) {
            logSignatures.add(signature);
            return;
        }
        if (value instanceof TempoQueryResult traceResult) {
            traces.add(traceResult);
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectEvidence(item, metricComparisons, logSignatures, traces, deployments, configChanges));
            return;
        }
        if (value instanceof DeploymentEvent deployment) {
            deployments.add(deployment);
            return;
        }
        if (value instanceof ConfigChange change) {
            configChanges.add(change);
        }
    }

    private Map<String, Object> stateUpdate(NodeStatus status, Object result, String error) {
        var update = new HashMap<String, Object>();
        update.put(Lg4jPlanExecutionState.RESULTS,
                result == null ? Map.of() : Map.of(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, result));
        update.put(Lg4jPlanExecutionState.STATUSES, Map.of(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, status));
        update.put(Lg4jPlanExecutionState.ERRORS,
                error == null || error.isBlank() ? Map.of() : Map.of(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, error));
        return update;
    }
}
