package dev.harness.agent.tools;

import dev.harness.agent.ai.AiUsageExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolCatalogTests {

    @Test
    void exposesSpringAiToolDefinitions() {
        ToolCatalog catalog = new ToolCatalog(new GameRecommendationTools(new GameRecommendationData(), null, new AiUsageExtractor()));

        assertThat(catalog.toolNames()).containsExactlyInAnyOrder(
                "get_genre_facts",
                "get_genre_reviews",
                "get_games",
                "get_prices",
                "summarizer_node"
        );
        assertThat(catalog.definitions())
                .allSatisfy(definition -> {
                    assertThat(definition.description()).isNotBlank();
                    assertThat(definition.inputSchema()).isNotBlank();
                });
        assertThat(catalog.finalSynthesisTool())
                .get()
                .extracting(ToolDefinitionView::name)
                .isEqualTo("summarizer_node");
        assertThat(catalog.requiredArgumentNames("summarizer_node"))
                .containsExactlyInAnyOrder("preferences", "genreFacts", "genreReviews", "games", "prices");
        assertThat(catalog.argumentNames("get_genre_facts")).isEmpty();
    }
}
