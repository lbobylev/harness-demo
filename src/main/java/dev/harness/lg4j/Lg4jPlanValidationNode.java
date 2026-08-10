package dev.harness.lg4j;

import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
class Lg4jPlanValidationNode {

    Map<String, Object> validate(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }

        var error = validatePlan(state.plan().orElse(null));
        if (error != null) {
            return validationFailed(error);
        }

        return Map.of();
    }

    private Map<String, Object> validationFailed(String error) {
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_VALIDATION,
                Lg4jRunState.ERROR_CLASS, ErrorClass.VALIDATION,
                Lg4jRunState.ERROR, error
        );
    }

    private static String validatePlan(Plan plan) {
        if (plan == null) {
            return "plan must not be null";
        }
        if (plan.nodes().isEmpty()) {
            return "plan must contain at least one node";
        }

        var ids = new HashSet<String>();
        var nodesById = new HashMap<String, PlanNode>();
        for (var node : plan.nodes()) {
            if (node == null) {
                return "plan must not contain null nodes";
            }
            if (node.getId() == null || node.getId().isBlank()) {
                return "plan node id must not be blank";
            }
            if (!ids.add(node.getId())) {
                return "duplicate plan node id: " + node.getId();
            }
            if (node.getTool() == null || node.getTool().isBlank()) {
                return "plan node tool must not be blank: " + node.getId();
            }
            if (!Lg4jToolSpecs.names().contains(node.getTool())) {
                return "unknown evidence tool: " + node.getTool();
            }
            nodesById.put(node.getId(), node);
        }

        for (var node : plan.nodes()) {
            var dependencyError = validateDependencies(node, ids);
            if (dependencyError != null) {
                return dependencyError;
            }
        }

        var cycleError = validateAcyclic(plan, nodesById);
        if (cycleError != null) {
            return cycleError;
        }

        var terminalNodes = terminalNodes(plan);
        if (terminalNodes.isEmpty()) {
            return "plan must contain at least one terminal evidence node";
        }
        for (var terminalNode : terminalNodes) {
            if (!Lg4jToolSpecs.terminalNames().contains(terminalNode.getTool())) {
                return "terminal evidence node must produce analysis-ready evidence: " + terminalNode.getId();
            }
        }

        return null;
    }

    private static Set<PlanNode> terminalNodes(Plan plan) {
        var dependencyIds = new HashSet<String>();
        for (var node : plan.nodes()) {
            dependencyIds.addAll(node.getDeps());
        }
        var terminals = new HashSet<PlanNode>();
        for (var node : plan.nodes()) {
            if (!dependencyIds.contains(node.getId())) {
                terminals.add(node);
            }
        }
        return terminals;
    }

    private static String validateDependencies(PlanNode node, Set<String> ids) {
        for (var dep : node.getDeps()) {
            if (!ids.contains(dep)) {
                return "unknown dependency '%s' in node '%s'".formatted(dep, node.getId());
            }
        }

        for (var binding : node.getArguments()) {
            if (binding == null || binding.value() == null) {
                continue;
            }
            var value = binding.value();
            if (value.type() != ArgumentValueType.NODE_RESULT) {
                continue;
            }
            if (value.sourceNodeId() == null || value.sourceNodeId().isBlank()) {
                return "NODE_RESULT sourceNodeId must not be blank in node " + node.getId();
            }
            if (!ids.contains(value.sourceNodeId())) {
                return "unknown NODE_RESULT source '%s' in node '%s'"
                        .formatted(value.sourceNodeId(), node.getId());
            }
            if (!node.getDeps().contains(value.sourceNodeId())) {
                return "NODE_RESULT source '%s' must be listed in deps for node '%s'"
                        .formatted(value.sourceNodeId(), node.getId());
            }
        }
        return null;
    }

    private static String validateAcyclic(Plan plan, Map<String, PlanNode> nodesById) {
        var visiting = new HashSet<String>();
        var visited = new HashSet<String>();
        for (var node : plan.nodes()) {
            var error = visit(node.getId(), nodesById, visiting, visited);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private static String visit(
            String nodeId,
            Map<String, PlanNode> nodesById,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(nodeId)) {
            return null;
        }
        if (!visiting.add(nodeId)) {
            return "plan contains dependency cycle at node: " + nodeId;
        }

        var node = nodesById.get(nodeId);
        if (node != null) {
            for (var depId : node.getDeps()) {
                var error = visit(depId, nodesById, visiting, visited);
                if (error != null) {
                    return error;
                }
            }
        }

        visiting.remove(nodeId);
        visited.add(nodeId);
        return null;
    }
}
