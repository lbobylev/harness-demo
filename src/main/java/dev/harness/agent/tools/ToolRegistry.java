package dev.harness.agent.tools;

import dev.harness.agent.run.HarnessErrorCode;
import org.springframework.stereotype.Component;

import java.util.Map;

import static dev.harness.agent.tools.GameRecommendationTools.ARG_GAMES;
import static dev.harness.agent.tools.GameRecommendationTools.ARG_GENRE_FACTS;
import static dev.harness.agent.tools.GameRecommendationTools.ARG_GENRE_REVIEWS;
import static dev.harness.agent.tools.GameRecommendationTools.ARG_PREFERENCES;
import static dev.harness.agent.tools.GameRecommendationTools.ARG_PRICES;
import static dev.harness.agent.tools.GameRecommendationTools.GET_GAMES;
import static dev.harness.agent.tools.GameRecommendationTools.GET_GENRE_FACTS;
import static dev.harness.agent.tools.GameRecommendationTools.GET_GENRE_REVIEWS;
import static dev.harness.agent.tools.GameRecommendationTools.GET_PRICES;
import static dev.harness.agent.tools.GameRecommendationTools.SUMMARIZER;

@Component
public class ToolRegistry implements ToolExecutor {

    private final GameRecommendationTools tools;

    private final ToolCatalog catalog;

    public ToolRegistry(GameRecommendationTools tools, ToolCatalog catalog) {
        this.tools = tools;
        this.catalog = catalog;
    }

    public boolean hasTool(String name) {
        return catalog.hasTool(name);
    }

    @Override
    public ToolExecutionResult execute(String name, Map<String, Object> args) {
        if (!hasTool(name)) {
            throw new ToolExecutionException(HarnessErrorCode.UNKNOWN_TOOL, "unknown tool: " + name);
        }

        Map<String, Object> safeArgs = args == null ? Map.of() : args;

        return switch (name) {
            case GET_GENRE_FACTS -> ToolExecutionResult.of(tools.getGenreFacts());
            case GET_GENRE_REVIEWS -> ToolExecutionResult.of(tools.getGenreReviews());
            case GET_GAMES -> ToolExecutionResult.of(tools.getGames());
            case GET_PRICES -> ToolExecutionResult.of(tools.getPrices());
            case SUMMARIZER -> tools.summarizeRecommendation(
                    requiredString(safeArgs, ARG_PREFERENCES),
                    requiredList(safeArgs, ARG_GENRE_FACTS),
                    requiredList(safeArgs, ARG_GENRE_REVIEWS),
                    requiredList(safeArgs, ARG_GAMES),
                    requiredList(safeArgs, ARG_PRICES));
            default -> throw new ToolExecutionException("unhandled tool: " + name);
        };
    }

    private static String requiredString(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        throw new ToolExecutionException(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT,
                "missing required string arg: " + key);
    }

    @SuppressWarnings("unchecked")
    private static <T> java.util.List<T> requiredList(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof java.util.List<?> list) {
            return (java.util.List<T>) list;
        }
        throw new ToolExecutionException(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT,
                "missing required list arg: " + key);
    }
}
