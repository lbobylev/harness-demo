package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

final class Lg4jPlanGraphBuilder {

    static final String ANALYZE_EVIDENCE = "lg4j_analyze_evidence";

    private static final String FORK = "lg4j_fork";
    private static final String COMPONENT_PREFIX = "lg4j_component_";

    StateGraph<Lg4jPlanExecutionState> build(
            Plan plan,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction,
            AsyncNodeAction<Lg4jPlanExecutionState> analyzeEvidence) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        var nodesById = Lg4jPlanDag.nodesById(plan);

        graph.addNode(FORK, node_async(state -> Map.of()));
        graph.addEdge(START, FORK);
        graph.addNode(ANALYZE_EVIDENCE, analyzeEvidence);

        var components = components(plan, nodesById);
        for (int i = 0; i < components.size(); i++) {
            var componentId = COMPONENT_PREFIX + i;
            graph.addNode(componentId, componentGraph(components.get(i), nodesById, nodeAction).compile());
            graph.addEdge(FORK, componentId);
            graph.addEdge(componentId, ANALYZE_EVIDENCE);
        }

        graph.addEdge(ANALYZE_EVIDENCE, END);
        return graph;
    }

    private StateGraph<Lg4jPlanExecutionState> componentGraph(
            Set<String> component,
            Map<String, PlanNode> nodesById,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        var nodes = component.stream().map(nodeId -> requireNode(nodesById, nodeId)).toList();

        for (var node : nodes) {
            graph.addNode(node.getId(), nodeAction.apply(node));
        }

        for (var node : nodes) {
            if (node.getDeps().isEmpty()) {
                graph.addEdge(START, node.getId());
            }
            for (var dependencyId : node.getDeps()) {
                if (component.contains(dependencyId)) {
                    graph.addEdge(dependencyId, node.getId());
                }
            }
        }

        var dependencyIds = new HashSet<String>();
        nodes.forEach(node -> dependencyIds.addAll(node.getDeps()));
        for (var node : nodes) {
            if (!dependencyIds.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    private List<Set<String>> components(Plan plan, Map<String, PlanNode> nodesById) {
        var remaining = new HashSet<String>(nodesById.keySet());
        var components = new ArrayList<Set<String>>();
        while (!remaining.isEmpty()) {
            var start = remaining.iterator().next();
            var component = new HashSet<String>();
            var queue = new ArrayDeque<String>();
            queue.add(start);
            remaining.remove(start);

            while (!queue.isEmpty()) {
                var nodeId = queue.removeFirst();
                component.add(nodeId);
                for (var neighbor : neighbors(plan, nodesById, nodeId)) {
                    if (remaining.remove(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private Set<String> neighbors(Plan plan, Map<String, PlanNode> nodesById, String nodeId) {
        var neighbors = new HashSet<String>();
        var node = requireNode(nodesById, nodeId);
        neighbors.addAll(node.getDeps());
        for (var candidate : plan.nodes()) {
            if (candidate.getDeps().contains(nodeId)) {
                neighbors.add(candidate.getId());
            }
        }
        return neighbors;
    }

    private PlanNode requireNode(Map<String, PlanNode> nodesById, String nodeId) {
        var node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("unknown plan node: " + nodeId);
        }
        return node;
    }
}
