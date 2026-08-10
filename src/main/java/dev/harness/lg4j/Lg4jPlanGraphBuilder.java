package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;

final class Lg4jPlanGraphBuilder {

    StateGraph<Lg4jPlanExecutionState> build(
            Plan plan,
            Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
        var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);
        for (var node : plan.nodes()) {
            graph.addNode(node.getId(), nodeAction.apply(node));
        }
        for (var node : plan.nodes()) {
            if (node.getDeps().isEmpty()) {
                graph.addEdge(START, node.getId());
            }
            for (var dependencyId : node.getDeps()) {
                graph.addEdge(dependencyId, node.getId());
            }
        }
        for (var terminalNode : terminalNodes(plan)) {
            graph.addEdge(terminalNode.getId(), END);
        }
        return graph;
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
