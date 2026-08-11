package dev.harness.agent.budget;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record BudgetSnapshot(
        Instant startedAt,
        Duration elapsed,
        long tokensUsed,
        long maxTokens,
        long agentInvocationsUsed,
        long maxAgentInvocations,
        BigDecimal estimatedCostUsd,
        BigDecimal maxEstimatedCostUsd,
        double pressure
) implements Serializable {
}
