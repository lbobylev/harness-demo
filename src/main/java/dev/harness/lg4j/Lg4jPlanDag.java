package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Lg4jPlanDag {

    private Lg4jPlanDag() {
    }

    static Map<String, PlanNode> nodesById(Plan plan) {
        return plan.nodes().stream().collect(Collectors.toMap(PlanNode::getId, Function.identity()));
    }

    static List<PlanNode> terminals(Plan plan) {
        var dependencyIds = new HashSet<String>();
        for (var node : plan.nodes()) {
            dependencyIds.addAll(node.getDeps());
        }
        return plan.nodes().stream()
                .filter(node -> !dependencyIds.contains(node.getId()))
                .toList();
    }

    static List<Set<String>> components(Plan plan) {
        var childrenById = childrenById(plan);
        var remaining = plan.nodes().stream()
                .map(PlanNode::getId)
                .collect(Collectors.toCollection(HashSet::new));
        var components = new ArrayList<Set<String>>();

        while (!remaining.isEmpty()) {
            var component = connectedComponent(remaining.iterator().next(), plan, childrenById, remaining);
            components.add(component);
        }

        return components;
    }

    private static Set<String> connectedComponent(
            String start,
            Plan plan,
            Map<String, Set<String>> childrenById,
            Set<String> remaining) {
        var component = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        remaining.remove(start);

        while (!queue.isEmpty()) {
            var nodeId = queue.removeFirst();
            component.add(nodeId);
            for (var connectedId : connectedIds(plan, childrenById, nodeId)) {
                if (remaining.remove(connectedId)) {
                    queue.add(connectedId);
                }
            }
        }

        return component;
    }

    private static Set<String> connectedIds(Plan plan, Map<String, Set<String>> childrenById, String nodeId) {
        var connected = new HashSet<String>();
        var node = plan.getNodeById(nodeId);
        if (node != null) {
            connected.addAll(node.getDeps());
        }
        connected.addAll(childrenById.getOrDefault(nodeId, Set.of()));
        return connected;
    }

    private static Map<String, Set<String>> childrenById(Plan plan) {
        Map<String, Set<String>> childrenById = plan.nodes().stream()
                .collect(Collectors.toMap(PlanNode::getId, ignored -> new HashSet<>()));
        for (var node : plan.nodes()) {
            for (var dependencyId : node.getDeps()) {
                var children = childrenById.get(dependencyId);
                if (children != null) {
                    children.add(node.getId());
                }
            }
        }
        return childrenById;
    }
}
