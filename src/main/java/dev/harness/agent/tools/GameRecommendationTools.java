package dev.harness.agent.tools;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.ai.AiUsageExtractor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GameRecommendationTools {

    public static final String GET_GENRE_FACTS = "get_genre_facts";
    public static final String GET_GENRE_REVIEWS = "get_genre_reviews";
    public static final String GET_GAMES = "get_games";
    public static final String GET_PRICES = "get_prices";
    public static final String SUMMARIZER = "summarizer_node";

    public static final String ARG_PREFERENCES = "preferences";
    public static final String ARG_GENRE_FACTS = "genreFacts";
    public static final String ARG_GENRE_REVIEWS = "genreReviews";
    public static final String ARG_GAMES = "games";
    public static final String ARG_PRICES = "prices";

    private final GameRecommendationData data;
    private final ChatClient chatClient;
    private final AiUsageExtractor usageExtractor;

    public GameRecommendationTools(GameRecommendationData data, ChatClient chatClient, AiUsageExtractor usageExtractor) {
        this.data = data;
        this.chatClient = chatClient;
        this.usageExtractor = usageExtractor;
    }

    @Tool(name = GET_GENRE_FACTS, description = "Get local facts for supported computer game genres.")
    public List<GenreFacts> getGenreFacts() {
        return data.genreFacts();
    }

    @Tool(name = GET_GENRE_REVIEWS, description = "Get local aggregated review summaries for supported computer game genres.")
    public List<GenreReviews> getGenreReviews() {
        return data.genreReviews();
    }

    @Tool(name = GET_GAMES, description = "Get local game catalog entries with title, genre, tags, and facts.")
    public List<GameInfo> getGames() {
        return data.games();
    }

    @Tool(name = GET_PRICES, description = "Get fake local prices for known games.")
    public List<GamePrice> getPrices() {
        return data.prices();
    }

    @Tool(name = SUMMARIZER, description = "Summarize game recommendations from user preferences, genre facts, reviews, games, and prices.")
    public ToolExecutionResult summarizeRecommendation(
            @ToolParam(description = "User game preferences") String preferences,
            @ToolParam(description = "Genre facts returned by " + GET_GENRE_FACTS) List<GenreFacts> genreFacts,
            @ToolParam(description = "Genre reviews returned by " + GET_GENRE_REVIEWS) List<GenreReviews> genreReviews,
            @ToolParam(description = "Games returned by " + GET_GAMES) List<GameInfo> games,
            @ToolParam(description = "Prices returned by " + GET_PRICES) List<GamePrice> prices) {
        String prompt = """
                Recommend 2-3 computer games for the user.
                Use the supplied genre facts, genre review summaries, game catalog, and prices.
                Do not invent prices.

                User preferences: %s
                Genre facts: %s
                Genre reviews: %s
                Games: %s
                Prices: %s
                """.formatted(preferences, genreFacts, genreReviews, games, prices);

        ChatResponse response = chatClient.prompt()
                .user(prompt)
                .call()
                .chatResponse();
        AiUsage usage = usageExtractor.extract(response);
        return new ToolExecutionResult(new RecommendationSummary(responseText(response)), usage);
    }

    private static String responseText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
