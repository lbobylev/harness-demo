package dev.harness.lg4j;

import static java.util.Collections.unmodifiableList;

import java.io.Serializable;
import java.util.List;

record Lg4jPlanShape(List<List<String>> branches, List<String> tail) implements Serializable {

    Lg4jPlanShape {
        branches = branches == null
                ? List.of()
                : branches.stream().map(this::toList).toList();
        tail = toList(tail);
    }

    private List<String> toList(List<String> list) {
        return list == null ? List.of() : unmodifiableList(list);
    }
}
