package dev.harness.lg4j.graph;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class Lg4jPlanDag {

    private Lg4jPlanDag() {
    }

    public static Map<String, PlanNode> nodesById(Plan plan) {
        return plan.nodes().stream().collect(Collectors.toMap(PlanNode::getId, Function.identity()));
    }

    public static List<PlanNode> terminals(Plan plan) {
        var dependencyIds = new HashSet<String>();
        for (var node : plan.nodes()) {
            dependencyIds.addAll(node.getDeps());
        }
        return plan.nodes().stream()
                .filter(node -> !dependencyIds.contains(node.getId()))
                .toList();
    }

    public static List<Set<String>> components(Plan plan) {
        var connectedById = connectedById(plan);
        var remaining = plan.nodes().stream()
                .map(PlanNode::getId)
                .collect(Collectors.toCollection(HashSet::new));
        var components = new ArrayList<Set<String>>();

        while (!remaining.isEmpty()) {
            var component = connectedComponent(remaining.iterator().next(), connectedById, remaining);
            components.add(component);
        }

        return components;
    }

    private static Set<String> connectedComponent(
            String start,
            Map<String, Set<String>> connectedById,
            Set<String> remaining) {
        var component = new HashSet<String>();
        var queue = new ArrayDeque<String>();
        queue.add(start);
        remaining.remove(start);

        while (!queue.isEmpty()) {
            var nodeId = queue.removeFirst();
            component.add(nodeId);
            for (var connectedId : connectedById.getOrDefault(nodeId, Set.of())) {
                if (remaining.remove(connectedId)) {
                    queue.add(connectedId);
                }
            }
        }

        return component;
    }

    private static Map<String, Set<String>> connectedById(Plan plan) {
        Map<String, Set<String>> connectedById = new HashMap<>();
        for (var node : plan.nodes()) {
            connectedById.put(node.getId(), new HashSet<>());
        }
        for (var node : plan.nodes()) {
            for (var dependencyId : node.getDeps()) {
                connectedById.get(node.getId()).add(dependencyId);
                var dependencyConnections = connectedById.get(dependencyId);
                if (dependencyConnections != null) {
                    dependencyConnections.add(node.getId());
                }
            }
        }
        return connectedById;
    }
}
