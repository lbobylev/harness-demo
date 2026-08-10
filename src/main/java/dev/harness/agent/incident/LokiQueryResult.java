package dev.harness.agent.incident;

import java.io.Serializable;
import java.util.List;

public record LokiQueryResult(
        String service,
        String query,
        String from,
        String to,
        List<LogEvent> entries
) implements Serializable {
    public LokiQueryResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
