package dev.harness.agent.orchestration;

public record RunRequest(
        String goal,
        String sessionId
) {
}
