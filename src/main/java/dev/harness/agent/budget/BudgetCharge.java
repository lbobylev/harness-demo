package dev.harness.agent.budget;

import java.math.BigDecimal;

public record BudgetCharge(
        long inputTokens,
        long outputTokens,
        long agentInvocations,
        BigDecimal estimatedCostUsd
) {

    public BudgetCharge {
        if (inputTokens < 0 || outputTokens < 0 || agentInvocations < 0) {
            throw new IllegalArgumentException("budget charge counts must not be negative");
        }
        estimatedCostUsd = estimatedCostUsd == null ? BigDecimal.ZERO : estimatedCostUsd;
        if (estimatedCostUsd.signum() < 0) {
            throw new IllegalArgumentException("estimatedCostUsd must not be negative");
        }
    }

    public static BudgetCharge agentInvocation() {
        return new BudgetCharge(0, 0, 1, BigDecimal.ZERO);
    }

}
