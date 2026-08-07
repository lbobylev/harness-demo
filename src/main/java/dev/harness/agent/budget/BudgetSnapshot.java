package dev.harness.agent.budget;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record BudgetSnapshot(
        Instant startedAt,
        Duration elapsed,
        long tokensUsed,
        long maxTokens,
        long toolCallsUsed,
        long maxToolCalls,
        BigDecimal estimatedCostUsd,
        BigDecimal maxEstimatedCostUsd,
        double pressure
) {
}
