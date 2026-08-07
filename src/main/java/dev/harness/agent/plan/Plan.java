package dev.harness.agent.plan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record Plan(List<PlanNode> nodes) {

    public Plan {
        nodes = nodes == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(nodes));
    }

    public PlanNode getNodeById(String id) {
        return nodes.stream()
                .filter(node -> node != null && id.equals(node.getId()))
                .findFirst().orElse(null);
    }

    public List<PlanNode> getDepNodes(PlanNode node) {
        return node.getDeps().stream().map(this::getNodeById).toList();
    }

    public Optional<PlanNode> getDepNodeByTool(PlanNode node, String tool) {
        return getDepNodes(node).stream()
                .filter(dep -> dep != null && tool.equals(dep.getTool()))
                .findFirst();
    }
}
