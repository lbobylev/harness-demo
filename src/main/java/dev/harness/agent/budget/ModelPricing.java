package dev.harness.agent.budget;

import java.math.BigDecimal;

public record ModelPricing(
        String model,
        BigDecimal inputTokenUsd,
        BigDecimal outputTokenUsd
) {

    public ModelPricing {
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model must not be blank");
        }
        if (inputTokenUsd == null || inputTokenUsd.signum() < 0) {
            throw new IllegalArgumentException("inputTokenUsd must not be negative");
        }
        if (outputTokenUsd == null || outputTokenUsd.signum() < 0) {
            throw new IllegalArgumentException("outputTokenUsd must not be negative");
        }
    }

    public BigDecimal estimateCostUsd(long inputTokens, long outputTokens) {
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }

        return inputTokenUsd.multiply(BigDecimal.valueOf(inputTokens))
                .add(outputTokenUsd.multiply(BigDecimal.valueOf(outputTokens)));
    }
}
