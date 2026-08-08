package dev.harness.agent.execution;

import dev.harness.agent.plan.PlanNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

final class SchedulerState {

    private final Map<String, PlanNode> nodesById;

    private final Map<String, List<PlanNode>> dependents;

    private final Map<String, AtomicInteger> remainingDeps;

    private final Map<String, AtomicInteger> failedDeps;

    private final AtomicInteger remainingNodes;

    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    SchedulerState(List<PlanNode> nodes) {
        var nodeIndex = new HashMap<String, PlanNode>();
        var dependentIndex = new HashMap<String, List<PlanNode>>();
        this.remainingDeps = new HashMap<>();
        this.failedDeps = new HashMap<>();

        for (PlanNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (nodeIndex.put(node.getId(), node) != null) {
                throw new IllegalArgumentException("duplicate node id: " + node.getId());
            }
            remainingDeps.put(node.getId(), new AtomicInteger(node.getDeps().size()));
            failedDeps.put(node.getId(), new AtomicInteger(0));
        }

        for (PlanNode node : nodeIndex.values()) {
            for (String dependencyId : node.getDeps()) {
                if (!nodeIndex.containsKey(dependencyId)) {
                    throw new IllegalArgumentException("unknown dependency: " + dependencyId);
                }
                dependentIndex.computeIfAbsent(dependencyId, ignored -> new ArrayList<>()).add(node);
            }
        }

        this.nodesById = Map.copyOf(nodeIndex);
        this.dependents = dependentIndex.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
        this.remainingNodes = new AtomicInteger(this.nodesById.size());
        if (this.nodesById.isEmpty()) {
            completion.complete(null);
        }
    }

    List<PlanNode> initialReadyNodes() {
        return nodesById.values().stream()
                .filter(node -> remainingDeps.get(node.getId()).get() == 0)
                .toList();
    }

    List<PlanNode> dependentsOf(PlanNode node) {
        return dependents.getOrDefault(node.getId(), List.of());
    }

    int dependencyCompleted(PlanNode child, boolean successful) {
        if (!successful) {
            failedDeps.get(child.getId()).incrementAndGet();
        }
        return remainingDeps.get(child.getId()).decrementAndGet();
    }

    boolean hasFailedDependencies(PlanNode node) {
        return failedDeps.get(node.getId()).get() > 0;
    }

    void nodeTerminal() {
        if (remainingNodes.decrementAndGet() == 0) {
            completion.complete(null);
        }
    }

    void awaitCompletion() {
        completion.join();
    }

}
