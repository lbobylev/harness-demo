package dev.harness.agent.incident;

import java.io.Serializable;

public record LogEvent(
        String id,
        String timestamp,
        String service,
        String level,
        String version,
        String message
) implements Serializable {
}
