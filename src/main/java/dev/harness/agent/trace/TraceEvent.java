package dev.harness.agent.trace;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

public record TraceEvent(
        Instant timestamp,
        String runId,
        String nodeId,
        String kind,
        Map<String, Object> attributes
) implements Serializable {
}
