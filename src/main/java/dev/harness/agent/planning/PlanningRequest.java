package dev.harness.agent.planning;

public record PlanningRequest(
        String goal,
        String toolCatalog,
        String failureContext
) {

    public PlanningRequest(String goal, String toolCatalog) {
        this(goal, toolCatalog, null);
    }
}
