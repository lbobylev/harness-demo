package dev.harness.agent.budget;

import java.math.BigDecimal;
import java.time.Duration;

public record BudgetLimits(
        long maxTokens,
        long maxToolCalls,
        Duration maxWallClock,
        BigDecimal maxEstimatedCostUsd
) {

    public BudgetLimits {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
        if (maxWallClock == null || maxWallClock.isZero() || maxWallClock.isNegative()) {
            throw new IllegalArgumentException("maxWallClock must be positive");
        }
        if (maxEstimatedCostUsd == null || maxEstimatedCostUsd.signum() <= 0) {
            throw new IllegalArgumentException("maxEstimatedCostUsd must be positive");
        }
    }
}
