package dev.harness.agent.validation;

import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_EVIDENCE;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_FROM;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_HYPOTHESIS_ASSESSMENT;
import static dev.harness.agent.tools.IncidentInvestigationTools.ARG_TO;
import static dev.harness.agent.tools.IncidentInvestigationTools.BUILD_INCIDENT_REPORT;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_LOKI;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_PROMETHEUS;
import static dev.harness.agent.tools.IncidentInvestigationTools.QUERY_TEMPO;

@Component
public class IncidentPolicyValidator {

    private final ToolCatalog toolCatalog;

    public IncidentPolicyValidator(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    public void validate(Plan plan) {
        if (plan == null) {
            throw new PlanValidationException("plan must not be null");
        }

        Map<String, PlanNode> nodesById = plan.nodes().stream()
                .filter(node -> node != null && node.getId() != null)
                .collect(Collectors.toMap(PlanNode::getId, node -> node));

        for (PlanNode node : nodesById.values()) {
            ToolRole role = toolCatalog.roleOf(node.getTool());
            if (role == ToolRole.FINAL_SYNTHESIS) {
                validateFinalReportNode(node, nodesById);
            }
            if (role == ToolRole.HYPOTHESIS_TEST) {
                validateHypothesisTestNode(node, nodesById);
            }
            if (isObservabilityQuery(node.getTool())) {
                validateBoundedQueryNode(node);
            }
        }
    }

    private void validateFinalReportNode(PlanNode node, Map<String, PlanNode> nodesById) {
        if (!BUILD_INCIDENT_REPORT.equals(node.getTool())) {
            throw new PlanValidationException("final synthesis tool must be " + BUILD_INCIDENT_REPORT);
        }
        requireNodeResultArgument(node, ARG_HYPOTHESIS_ASSESSMENT, source -> roleOf(nodesById, source) == ToolRole.HYPOTHESIS_TEST,
                "build_incident_report must consume hypothesisAssessment from a HYPOTHESIS_TEST node");
        requireNodeResultArgument(node, ARG_EVIDENCE, source -> isEvidenceOrAnalysis(nodesById, source),
                "build_incident_report must consume evidence from an EVIDENCE or ANALYSIS node");
    }

    private void validateHypothesisTestNode(PlanNode node, Map<String, PlanNode> nodesById) {
        boolean hasEvidenceInput = node.getArguments().stream()
                .filter(argument -> argument != null && argument.value() != null)
                .filter(argument -> argument.value().type() == ArgumentValueType.NODE_RESULT)
                .map(argument -> argument.value().sourceNodeId())
                .anyMatch(source -> isEvidenceOrAnalysis(nodesById, source));
        if (!hasEvidenceInput) {
            throw new PlanValidationException("hypothesis test must consume NODE_RESULT evidence from an EVIDENCE or ANALYSIS node");
        }
    }

    private static void validateBoundedQueryNode(PlanNode node) {
        requireLiteralArgument(node, ARG_FROM);
        requireLiteralArgument(node, ARG_TO);
    }

    private void requireNodeResultArgument(
            PlanNode node,
            String argumentName,
            Predicate<String> sourcePredicate,
            String message) {
        boolean valid = node.getArguments().stream()
                .filter(argument -> argumentName.equals(argument.argumentName()))
                .map(ArgumentBinding::value)
                .filter(value -> value != null && value.type() == ArgumentValueType.NODE_RESULT)
                .map(value -> value.sourceNodeId())
                .anyMatch(source -> source != null && sourcePredicate.test(source));
        if (!valid) {
            throw new PlanValidationException(message);
        }
    }

    private static void requireLiteralArgument(PlanNode node, String argumentName) {
        boolean valid = node.getArguments().stream()
                .filter(argument -> argumentName.equals(argument.argumentName()))
                .map(ArgumentBinding::value)
                .anyMatch(value -> value != null
                        && value.type() == ArgumentValueType.LITERAL
                        && value.literalValue() != null
                        && !value.literalValue().isBlank());
        if (!valid) {
            throw new PlanValidationException("node %s must include bounded literal %s argument"
                    .formatted(node.getId(), argumentName));
        }
    }

    private ToolRole roleOf(Map<String, PlanNode> nodesById, String nodeId) {
        PlanNode node = nodesById.get(nodeId);
        return node == null ? ToolRole.DATA : toolCatalog.roleOf(node.getTool());
    }

    private boolean isEvidenceOrAnalysis(Map<String, PlanNode> nodesById, String nodeId) {
        ToolRole role = roleOf(nodesById, nodeId);
        return role == ToolRole.EVIDENCE || role == ToolRole.ANALYSIS;
    }

    private static boolean isObservabilityQuery(String tool) {
        return QUERY_PROMETHEUS.equals(tool) || QUERY_LOKI.equals(tool) || QUERY_TEMPO.equals(tool);
    }
}
