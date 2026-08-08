package dev.harness.agent.validation;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
public class DagValidator {

    private final ToolCatalog toolCatalog;

    public DagValidator(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    public void validate(Plan plan) {
        if (plan == null) {
            throw new PlanValidationException("plan must not be null");
        }

        Map<String, PlanNode> nodesById = indexNodes(plan);
        int finalNodeCount = 0;
        for (PlanNode node : nodesById.values()) {
            validateDependenciesExist(node, nodesById);
            validateToolExists(node);
            validateArguments(node, nodesById);
            if (toolCatalog.roleOf(node.getTool()) == ToolRole.FINAL_SYNTHESIS) {
                finalNodeCount++;
            }
        }
        validateNoCycles(nodesById);
        validateSingleFinalNode(finalNodeCount);
    }

    private static Map<String, PlanNode> indexNodes(Plan plan) {
        Map<String, PlanNode> nodesById = new LinkedHashMap<>();

        for (PlanNode node : plan.nodes()) {
            if (node == null) {
                throw new PlanValidationException("plan contains null node");
            }
            if (node.getId() == null || node.getId().isBlank()) {
                throw new PlanValidationException("node id must not be blank");
            }
            if (nodesById.putIfAbsent(node.getId(), node) != null) {
                throw new PlanValidationException("duplicate node id: " + node.getId());
            }
        }

        if (nodesById.isEmpty()) {
            throw new PlanValidationException("plan must contain at least one node");
        }

        return nodesById;
    }

    private static void validateDependenciesExist(PlanNode node, Map<String, PlanNode> nodesById) {
        for (String dep : node.getDeps()) {
            if (dep == null || dep.isBlank()) {
                throw new PlanValidationException("node %s dependency id must not be blank".formatted(node.getId()));
            }
            if (!nodesById.containsKey(dep)) {
                throw new PlanValidationException("node %s depends on missing node: %s".formatted(node.getId(), dep));
            }
        }
    }

    private void validateToolExists(PlanNode node) {
        if (node.getTool() == null || node.getTool().isBlank()) {
            throw new PlanValidationException("node %s tool must not be blank".formatted(node.getId()));
        }
        if (!toolCatalog.hasTool(node.getTool())) {
            throw new PlanValidationException("node %s references unknown tool: %s".formatted(node.getId(), node.getTool()));
        }
    }

    private void validateArguments(PlanNode node, Map<String, PlanNode> nodesById) {
        Set<String> argumentNames = new HashSet<>();
        Set<String> knownArgumentNames = toolCatalog.argumentNames(node.getTool());
        for (ArgumentBinding argument : node.getArguments()) {
            if (argument == null) {
                throw new PlanValidationException("node %s contains null argument".formatted(node.getId()));
            }
            if (argument.argumentName() == null || argument.argumentName().isBlank()) {
                throw new PlanValidationException("node %s argument name must not be blank".formatted(node.getId()));
            }
            if (!knownArgumentNames.contains(argument.argumentName())) {
                throw new PlanValidationException("node %s references unknown argument for tool %s: %s"
                        .formatted(node.getId(), node.getTool(), argument.argumentName()));
            }
            if (!argumentNames.add(argument.argumentName())) {
                throw new PlanValidationException("node %s contains duplicate argument: %s"
                        .formatted(node.getId(), argument.argumentName()));
            }
            validateArgumentValue(node, argument.value(), nodesById);
        }
        validateRequiredArgumentsPresent(node, argumentNames);
    }

    private void validateRequiredArgumentsPresent(PlanNode node, Set<String> argumentNames) {
        Set<String> missing = new HashSet<>(toolCatalog.requiredArgumentNames(node.getTool()));
        missing.removeAll(argumentNames);
        if (!missing.isEmpty()) {
            throw new PlanValidationException("node %s missing required arguments for tool %s: %s"
                    .formatted(node.getId(), node.getTool(), missing));
        }
    }

    private static void validateArgumentValue(PlanNode node, ArgumentValue value, Map<String, PlanNode> nodesById) {
        if (value == null) {
            throw new PlanValidationException("node %s argument value must not be null".formatted(node.getId()));
        }
        if (value.type() == null) {
            throw new PlanValidationException("node %s argument value type must not be null".formatted(node.getId()));
        }

        if (value.type() == ArgumentValueType.LITERAL) {
            if (value.literalValue() == null || value.literalValue().isBlank()) {
                throw new PlanValidationException("node %s literal argument value must not be blank".formatted(node.getId()));
            }
            if (value.sourceNodeId() != null && !value.sourceNodeId().isBlank()) {
                throw new PlanValidationException("node %s literal argument must not reference a node".formatted(node.getId()));
            }
            return;
        }

        if (value.sourceNodeId() == null || value.sourceNodeId().isBlank()) {
            throw new PlanValidationException("node %s node-result argument source node must not be blank".formatted(node.getId()));
        }
        if (value.literalValue() != null && !value.literalValue().isBlank()) {
            throw new PlanValidationException("node %s node-result argument must not include a literal value".formatted(node.getId()));
        }
        if (!nodesById.containsKey(value.sourceNodeId())) {
            throw new PlanValidationException("node %s argument references missing node: %s"
                    .formatted(node.getId(), value.sourceNodeId()));
        }
        if (!node.getDeps().contains(value.sourceNodeId())) {
            throw new PlanValidationException("node %s argument source node must be listed in deps: %s"
                    .formatted(node.getId(), value.sourceNodeId()));
        }
    }

    private static void validateNoCycles(Map<String, PlanNode> nodesById) {
        Map<String, VisitState> states = new HashMap<>();
        for (String id : nodesById.keySet()) {
            states.put(id, VisitState.UNVISITED);
        }

        for (String id : nodesById.keySet()) {
            if (states.get(id) == VisitState.UNVISITED) {
                visit(id, nodesById, states);
            }
        }
    }

    private static void visit(String id, Map<String, PlanNode> nodesById, Map<String, VisitState> states) {
        VisitState state = states.get(id);
        if (state == VisitState.VISITING) {
            throw new PlanValidationException("cycle detected at node: " + id);
        }
        if (state == VisitState.VISITED) {
            return;
        }

        states.put(id, VisitState.VISITING);
        for (String dep : nodesById.get(id).getDeps()) {
            visit(dep, nodesById, states);
        }
        states.put(id, VisitState.VISITED);
    }

    private static void validateSingleFinalNode(int count) {
        if (count != 1) {
            throw new PlanValidationException("plan must contain exactly one FINAL_SYNTHESIS node");
        }
    }

    private enum VisitState {
        UNVISITED,
        VISITING,
        VISITED
    }
}
