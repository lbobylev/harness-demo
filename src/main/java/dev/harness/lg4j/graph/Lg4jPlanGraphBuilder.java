package dev.harness.lg4j.graph;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.lg4j.state.Lg4jPlanExecutionState;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class Lg4jPlanGraphBuilder {

    public static final String ANALYZE_EVIDENCE = "lg4j_analyze_evidence";

    public static final String FORK = "lg4j_fork";
    private static final String COMPONENT_PREFIX = "lg4j_component_";

    public StateGraph<Lg4jPlanExecutionState> build(
            Plan plan,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction,
            AsyncNodeAction<Lg4jPlanExecutionState> analyzeEvidence) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        var nodesById = Lg4jPlanDag.nodesById(plan);
        var terminalIds = Lg4jPlanDag.terminals(plan).stream()
                .map(PlanNode::getId)
                .collect(Collectors.toSet());

        graph.addNode(FORK, node_async(state -> Map.of()));
        graph.addEdge(START, FORK);
        graph.addNode(ANALYZE_EVIDENCE, analyzeEvidence);

        var components = Lg4jPlanDag.components(plan);
        for (int i = 0; i < components.size(); i++) {
            var componentId = COMPONENT_PREFIX + i;
            graph.addNode(componentId, componentGraph(components.get(i), nodesById, terminalIds, nodeAction).compile());
            graph.addEdge(FORK, componentId);
            graph.addEdge(componentId, ANALYZE_EVIDENCE);
        }

        graph.addEdge(ANALYZE_EVIDENCE, END);
        return graph;
    }

    private StateGraph<Lg4jPlanExecutionState> componentGraph(
            Set<String> component,
            Map<String, PlanNode> nodesById,
            Set<String> terminalIds,
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

        for (var node : nodes) {
            if (terminalIds.contains(node.getId())) {
                graph.addEdge(node.getId(), END);
            }
        }

        return graph;
    }

    private PlanNode requireNode(Map<String, PlanNode> nodesById, String nodeId) {
        var node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("unknown plan node: " + nodeId);
        }
        return node;
    }
}
