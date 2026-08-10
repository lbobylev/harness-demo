package dev.harness.agent.incident;

import java.io.Serializable;
import java.util.List;

public record TempoQueryResult(
        String service,
        String query,
        String from,
        String to,
        List<TraceSpan> spans
) implements Serializable {
    public TempoQueryResult {
        spans = spans == null ? List.of() : List.copyOf(spans);
    }
}
