package dev.harness.agent.budget;

import dev.harness.agent.ai.AiUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetTests {

    @Test
    void pressureUsesMaximumUtilizationAcrossDimensions() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        budget.charge(new BudgetCharge(20, 10, 8, new BigDecimal("0.25")));

        assertThat(budget.pressure()).isEqualTo(0.8);
    }

    @Test
    void pressureIncludesWallClockUtilization() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-07T00:00:00Z"));
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofSeconds(10),
                new BigDecimal("1.00")
        ), clock);

        clock.advance(Duration.ofSeconds(7));

        assertThat(budget.pressure()).isEqualTo(0.7);
    }

    @Test
    void reportsRemainingWallClock() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-07T00:00:00Z"));
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofSeconds(10),
                new BigDecimal("1.00")
        ), clock);

        clock.advance(Duration.ofSeconds(3));

        assertThat(budget.remainingWallClock()).isEqualTo(Duration.ofSeconds(7));
        assertThat(budget.wallClockExhausted()).isFalse();
    }

    @Test
    void remainingWallClockDoesNotGoNegative() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-07T00:00:00Z"));
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofSeconds(10),
                new BigDecimal("1.00")
        ), clock);

        clock.advance(Duration.ofSeconds(12));

        assertThat(budget.remainingWallClock()).isZero();
        assertThat(budget.wallClockExhausted()).isTrue();
    }

    @Test
    void pressureIncludesEstimatedCostUtilization() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("2.00")
        ));

        budget.charge(new BudgetCharge(0, 0, 0, new BigDecimal("1.50")));

        assertThat(budget.pressure()).isEqualTo(0.75);
    }

    @Test
    void pressureIncludesToolCallUtilizationAtBoundary() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                4,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        budget.charge(new BudgetCharge(0, 0, 4, BigDecimal.ZERO));

        assertThat(budget.pressure()).isEqualTo(1.0);
        assertThat(budget.hasRoom()).isFalse();
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    void tryChargeToolCallDoesNotExceedLimit() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                1,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        assertThat(budget.tryChargeToolCall()).isTrue();
        assertThat(budget.tryChargeToolCall()).isFalse();

        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(1);
    }

    @Test
    void exhaustedWhenPressureReachesOne() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        budget.charge(new BudgetCharge(100, 0, 0, BigDecimal.ZERO));

        assertThat(budget.hasRoom()).isFalse();
        assertThat(budget.exhausted()).isTrue();
    }

    @Test
    void detectsHighPressureThreshold() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        budget.charge(new BudgetCharge(90, 0, 0, BigDecimal.ZERO));

        assertThat(budget.pressureAtOrAbove(0.9)).isTrue();
        assertThat(budget.pressureAtOrAbove(0.95)).isFalse();
    }

    @Test
    void rejectsNegativeHighPressureThreshold() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        assertThatThrownBy(() -> budget.pressureAtOrAbove(-0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("threshold");
    }

    @Test
    void tokenChargeUsesConfiguredPricing() {
        ModelPricing pricing = new ModelPricing(
                "test-model",
                new BigDecimal("0.01"),
                new BigDecimal("0.02")
        );
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ), pricing);

        budget.chargeTokens(10, 5);

        assertThat(budget.snapshot().estimatedCostUsd()).isEqualByComparingTo("0.20");
    }

    @Test
    void tokenChargeRequiresBudgetPricing() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));

        assertThatThrownBy(() -> budget.chargeTokens(10, 5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model pricing");
    }

    @Test
    void usageChargeUsesBudgetPricing() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ), new ModelPricing(
                "test-model",
                new BigDecimal("0.01"),
                new BigDecimal("0.02")
        ));

        budget.chargeUsage(new AiUsage("test-model", 10, 5, 15));

        assertThat(budget.snapshot().tokensUsed()).isEqualTo(15);
        assertThat(budget.snapshot().estimatedCostUsd()).isEqualByComparingTo("0.20");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
