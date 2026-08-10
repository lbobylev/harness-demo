package dev.harness.agent.incident;

import java.io.Serializable;

public record DeploymentEvent(
        String id,
        String timestamp,
        String service,
        String version,
        String changeId
) implements Serializable {
}
