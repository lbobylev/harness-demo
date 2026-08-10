package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

final class Lg4jPlanDag {

    private Lg4jPlanDag() {
    }

    static Map<String, PlanNode> nodesById(Plan plan) {
        return plan.nodes().stream().collect(Collectors.toMap(PlanNode::getId, Function.identity()));
    }

    static List<PlanNode> roots(Plan plan) {
        return plan.nodes().stream()
                .filter(node -> node.getDeps().isEmpty())
                .toList();
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
}
