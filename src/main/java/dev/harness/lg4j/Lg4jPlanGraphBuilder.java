package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

final class Lg4jPlanGraphBuilder {

    private static final String FORK = "lg4j_fork";
    private static final String BRANCH_PREFIX = "lg4j_branch_";

    StateGraph<Lg4jPlanExecutionState> build(
            Plan plan,
            Lg4jPlanShape shape,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        var nodesById = nodesById(plan);

        graph.addNode(FORK, node_async(state -> Map.of()));
        graph.addEdge(START, FORK);

        for (int i = 0; i < shape.branches().size(); i++) {
            var branchId = BRANCH_PREFIX + i;
            graph.addNode(branchId, branchGraph(shape.branches().get(i), nodesById, nodeAction).compile());
            graph.addEdge(FORK, branchId);
            graph.addEdge(branchId, shape.tail().getFirst());
        }

        for (var nodeId : shape.tail()) {
            graph.addNode(nodeId, nodeAction.apply(requireNode(nodesById, nodeId)));
        }

        for (int i = 1; i < shape.tail().size(); i++) {
            graph.addEdge(shape.tail().get(i - 1), shape.tail().get(i));
        }

        graph.addEdge(shape.tail().getLast(), END);
        return graph;
    }

    private StateGraph<Lg4jPlanExecutionState> branchGraph(
            List<String> branch,
            Map<String, PlanNode> nodesById,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
        if (branch.isEmpty()) {
            throw new IllegalArgumentException("plan branch must not be empty");
        }

        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        for (var nodeId : branch) {
            graph.addNode(nodeId, nodeAction.apply(requireNode(nodesById, nodeId)));
        }

        graph.addEdge(START, branch.getFirst());
        for (int i = 1; i < branch.size(); i++) {
            graph.addEdge(branch.get(i - 1), branch.get(i));
        }
        graph.addEdge(branch.getLast(), END);
        return graph;
    }

    private Map<String, PlanNode> nodesById(Plan plan) {
        return plan.nodes().stream().collect(Collectors.toMap(PlanNode::getId, Function.identity()));
    }

    private PlanNode requireNode(Map<String, PlanNode> nodesById, String nodeId) {
        var node = nodesById.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("unknown plan node: " + nodeId);
        }
        return node;
    }
}
