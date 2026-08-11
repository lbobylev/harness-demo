package dev.harness.lg4j.nodes;

import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.lg4j.agents.Lg4jAgentSpecs;
import dev.harness.lg4j.graph.Lg4jPlanDag;
import dev.harness.lg4j.state.Lg4jRunState;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component
public class Lg4jPlanValidationNode {

    public Map<String, Object> validate(Lg4jRunState state) {
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
            if (node.getAgent() == null || node.getAgent().isBlank()) {
                return "plan node agent must not be blank: " + node.getId();
            }
            if (!Lg4jAgentSpecs.names().contains(node.getAgent())) {
                return "unknown evidence agent: " + node.getAgent();
            }
        }

        var nodesById = Lg4jPlanDag.nodesById(plan);
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

        var terminalNodes = Lg4jPlanDag.terminals(plan);
        if (terminalNodes.isEmpty()) {
            return "plan must contain at least one terminal evidence node";
        }
        for (var terminalNode : terminalNodes) {
            if (!Lg4jAgentSpecs.terminalNames().contains(terminalNode.getAgent())) {
                return "terminal evidence node must produce analysis-ready evidence: " + terminalNode.getId();
            }
        }

        return null;
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
