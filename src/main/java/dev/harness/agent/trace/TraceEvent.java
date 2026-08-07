package dev.harness.agent.trace;

import dev.harness.agent.budget.BudgetSnapshot;

import java.time.Instant;
import java.util.Map;

public record TraceEvent(
        Instant timestamp,
        String runId,
        String sessionId,
        String kind,
        String role,
        String nodeId,
        String status,
        Long latencyMs,
        String message,
        BudgetSnapshot budget,
        Map<String, Object> data
) {

    public TraceEvent {
        timestamp = timestamp == null ? Instant.now() : timestamp;
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static TraceEvent of(String runId, String kind, String role, BudgetSnapshot budget) {
        return new TraceEvent(Instant.now(), runId, null, kind, role, null, null, null, null, budget, Map.of());
    }
}
