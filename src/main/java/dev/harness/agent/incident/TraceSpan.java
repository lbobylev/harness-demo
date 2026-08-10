package dev.harness.agent.incident;

import java.io.Serializable;

public record TraceSpan(
        String id,
        String traceId,
        String timestamp,
        String service,
        String operation,
        String spanName,
        String downstreamService,
        long durationMs,
        String status,
        String error
) implements Serializable {
}
