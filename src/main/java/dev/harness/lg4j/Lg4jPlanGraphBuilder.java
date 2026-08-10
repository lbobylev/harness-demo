package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

final class Lg4jPlanGraphBuilder {

    private static final String START_FAN_OUT = "lg4j_start";

    StateGraph<Lg4jPlanExecutionState> build(
            Plan plan,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        validateDependencies(plan);
        var rootNodes = rootNodes(plan);
        if (rootNodes.isEmpty()) {
            throw new IllegalArgumentException("plan must have at least one root node");
        }
        for (var node : plan.nodes()) {
            graph.addNode(node.getId(), nodeAction.apply(node));
        }
        graph.addNode(START_FAN_OUT, node_async(state -> Map.of()));
        graph.addEdge(START, START_FAN_OUT);
        for (var node : plan.nodes()) {
            if (node.getDeps().isEmpty()) {
                graph.addEdge(START_FAN_OUT, node.getId());
            }
            for (var dependencyId : node.getDeps()) {
                graph.addEdge(dependencyId, node.getId());
            }
        }
        var terminalNodes = terminalNodes(plan);
        if (terminalNodes.size() != 1) {
            throw new IllegalArgumentException("plan must have exactly one terminal node, found " + terminalNodes.size());
        }
        graph.addEdge(terminalNodes.iterator().next().getId(), END);
        return graph;
    }

    private void validateDependencies(Plan plan) {
        var nodeIds = plan.nodes().stream()
                .map(PlanNode::getId)
                .collect(Collectors.toSet());

        for (var node : plan.nodes()) {
            for (var dependencyId : node.getDeps()) {
                if (!nodeIds.contains(dependencyId)) {
                    throw new IllegalArgumentException("unknown dependency '%s' in node '%s'"
                            .formatted(dependencyId, node.getId()));
                }
            }
        }
    }

    private Set<PlanNode> rootNodes(Plan plan) {
        var roots = new HashSet<PlanNode>();
        for (var node : plan.nodes()) {
            if (node.getDeps().isEmpty()) {
                roots.add(node);
            }
        }
        return roots;
    }

    private Set<PlanNode> terminalNodes(Plan plan) {
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
}
