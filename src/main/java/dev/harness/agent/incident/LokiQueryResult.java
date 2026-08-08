package dev.harness.agent.incident;

import java.util.List;

public record LokiQueryResult(
        String service,
        String query,
        String from,
        String to,
        List<LogEvent> entries
) {
    public LokiQueryResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
