package dev.harness.agent.tools;

import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.run.HarnessErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTests {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        GameRecommendationTools tools = new GameRecommendationTools(new GameRecommendationData(), null, new AiUsageExtractor());
        registry = new ToolRegistry(tools, new ToolCatalog(tools));
    }

    @Test
    void executesKnownGamesTool() {
        ToolExecutionResult result = registry.execute("get_games", Map.of());

        assertThat(result.usage()).isEqualTo(dev.harness.agent.ai.AiUsage.none());
        assertThat(result.value()).isInstanceOf(java.util.List.class);
        assertThat((java.util.List<?>) result.value())
                .first()
                .isInstanceOf(GameInfo.class);
    }

    @Test
    void rejectsUnknownTool() {
        assertThatThrownBy(() -> registry.execute("delete_database", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo(HarnessErrorCode.UNKNOWN_TOOL);
        assertThatThrownBy(() -> registry.execute("delete_database", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void rejectsMissingRequiredSummaryArg() {
        assertThatThrownBy(() -> registry.execute("summarizer_node", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(exception -> ((ToolExecutionException) exception).errorCode())
                .isEqualTo(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT);
        assertThatThrownBy(() -> registry.execute("summarizer_node", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("preferences");
    }

    @Test
    void returnsFakePricesForKnownGames() {
        ToolExecutionResult result = registry.execute("get_prices", Map.of());

        assertThat((java.util.List<?>) result.value())
                .filteredOn(GamePrice.class::isInstance)
                .map(GamePrice.class::cast)
                .anySatisfy(price -> {
                    assertThat(price.title()).isEqualTo("Outer Wilds");
                    assertThat(price.currency()).isEqualTo("USD");
                    assertThat(price.price()).isEqualByComparingTo("24.99");
                });
    }
}
