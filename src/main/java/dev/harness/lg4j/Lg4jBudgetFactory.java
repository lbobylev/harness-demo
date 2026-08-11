package dev.harness.lg4j;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
class Lg4jBudgetFactory {

    private final BudgetLimits budgetLimits;
    private final ModelPricing modelPricing;

    Lg4jBudgetFactory(
            @Value("${harness.budget.max-tokens:20000}") long maxTokens,
            @Value("${harness.budget.max-tool-calls:50}") long maxToolCalls,
            @Value("${harness.budget.max-wall-clock:60s}") Duration maxWallClock,
            @Value("${harness.budget.max-estimated-cost-usd:0.25}") BigDecimal maxEstimatedCostUsd,
            @Value("${harness.pricing.model:gpt-4.1-mini}") String model,
            @Value("${harness.pricing.input-token-usd:0.0000004}") BigDecimal inputTokenUsd,
            @Value("${harness.pricing.output-token-usd:0.0000016}") BigDecimal outputTokenUsd) {
        this.budgetLimits = new BudgetLimits(maxTokens, maxToolCalls, maxWallClock, maxEstimatedCostUsd);
        this.modelPricing = new ModelPricing(model, inputTokenUsd, outputTokenUsd);
    }

    Budget create() {
        return new Budget(budgetLimits, modelPricing);
    }
}
