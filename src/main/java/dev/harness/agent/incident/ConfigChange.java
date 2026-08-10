package dev.harness.agent.incident;

import java.io.Serializable;

public record ConfigChange(
        String id,
        String timestamp,
        String service,
        String key,
        String oldValue,
        String newValue
) implements Serializable {
}
