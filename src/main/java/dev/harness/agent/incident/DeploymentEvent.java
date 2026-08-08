package dev.harness.agent.incident;

public record DeploymentEvent(
        String id,
        String timestamp,
        String service,
        String version,
        String changeId
) {
}
