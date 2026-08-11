package dev.harness.agent.budget;

import dev.harness.agent.ai.AiUsage;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class Budget {

    private final BudgetLimits limits;

    private final ModelPricing modelPricing;

    private final Clock clock;

    private final Instant startedAt;

    private long inputTokensUsed;

    private long outputTokensUsed;

    private long agentInvocationsUsed;

    private BigDecimal estimatedCostUsd = BigDecimal.ZERO;

    public Budget(BudgetLimits limits) {
        this(limits, null, Clock.systemUTC());
    }

    public Budget(BudgetLimits limits, ModelPricing modelPricing) {
        this(limits, modelPricing, Clock.systemUTC());
    }

    public Budget(BudgetLimits limits, Clock clock) {
        this(limits, null, clock);
    }

    public Budget(BudgetLimits limits, ModelPricing modelPricing, Clock clock) {
        if (limits == null) {
            throw new IllegalArgumentException("limits must not be null");
        }
        this.limits = limits;
        this.modelPricing = modelPricing;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.startedAt = Instant.now(this.clock);
    }

    public synchronized void charge(BudgetCharge charge) {
        if (charge == null) {
            return;
        }

        apply(charge);
    }

    public synchronized boolean tryCharge(BudgetCharge charge) {
        if (charge == null) {
            return true;
        }
        if (!canApply(charge)) {
            return false;
        }

        apply(charge);
        return true;
    }

    public synchronized boolean tryChargeAgentInvocation() {
        return tryCharge(BudgetCharge.agentInvocation());
    }

    private void apply(BudgetCharge charge) {
        inputTokensUsed += charge.inputTokens();
        outputTokensUsed += charge.outputTokens();
        agentInvocationsUsed += charge.agentInvocations();
        estimatedCostUsd = estimatedCostUsd.add(charge.estimatedCostUsd());
    }

    private boolean canApply(BudgetCharge charge) {
        if (elapsed().compareTo(limits.maxWallClock()) >= 0) {
            return false;
        }
        long tokensAfterCharge = tokensUsed() + charge.inputTokens() + charge.outputTokens();
        long agentInvocationsAfterCharge = agentInvocationsUsed + charge.agentInvocations();
        BigDecimal costAfterCharge = estimatedCostUsd.add(charge.estimatedCostUsd());

        return tokensAfterCharge <= limits.maxTokens()
                && agentInvocationsAfterCharge <= limits.maxAgentInvocations()
                && costAfterCharge.compareTo(limits.maxEstimatedCostUsd()) <= 0;
    }

    public synchronized void chargeAgentInvocation() {
        charge(BudgetCharge.agentInvocation());
    }

    public synchronized void chargeTokens(long inputTokens, long outputTokens) {
        if (modelPricing == null) {
            throw new IllegalStateException("model pricing is required to charge tokens");
        }
        charge(new BudgetCharge(inputTokens, outputTokens, 0,
                modelPricing.estimateCostUsd(inputTokens, outputTokens)));
    }

    public synchronized void chargeUsage(AiUsage usage) {
        if (usage == null || usage.totalTokens() == 0) {
            return;
        }
        chargeTokens(usage.inputTokens(), usage.outputTokens());
    }

    public synchronized boolean hasRoom() {
        return pressure() < 1.0;
    }

    public synchronized boolean exhausted() {
        return !hasRoom();
    }

    public synchronized boolean wallClockExhausted() {
        return remainingWallClock().isZero();
    }

    public synchronized Duration remainingWallClock() {
        var remaining = limits.maxWallClock().minus(elapsed());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public synchronized boolean pressureAtOrAbove(double threshold) {
        if (Double.isNaN(threshold) || threshold < 0.0) {
            throw new IllegalArgumentException("threshold must be non-negative");
        }
        return pressure() >= threshold;
    }

    public synchronized double pressure() {
        return max(
                tokenPressure(),
                agentInvocationPressure(),
                wallClockPressure(),
                estimatedCostPressure()
        );
    }

    public synchronized BudgetSnapshot snapshot() {
        return new BudgetSnapshot(
                startedAt,
                elapsed(),
                tokensUsed(),
                limits.maxTokens(),
                agentInvocationsUsed,
                limits.maxAgentInvocations(),
                estimatedCostUsd,
                limits.maxEstimatedCostUsd(),
                pressure()
        );
    }

    public synchronized long tokensUsed() {
        return inputTokensUsed + outputTokensUsed;
    }

    public synchronized Duration elapsed() {
        return Duration.between(startedAt, Instant.now(clock));
    }

    private double tokenPressure() {
        return (double) tokensUsed() / limits.maxTokens();
    }

    private double agentInvocationPressure() {
        return (double) agentInvocationsUsed / limits.maxAgentInvocations();
    }

    private double wallClockPressure() {
        return (double) elapsed().toMillis() / limits.maxWallClock().toMillis();
    }

    private double estimatedCostPressure() {
        return estimatedCostUsd.divide(limits.maxEstimatedCostUsd(), 8, java.math.RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double max(double first, double... rest) {
        double result = first;
        for (double value : rest) {
            result = Math.max(result, value);
        }
        return result;
    }
}
