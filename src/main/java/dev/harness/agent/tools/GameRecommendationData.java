package dev.harness.agent.tools;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class GameRecommendationData {

    private static final String USD = "USD";

    private final List<GenreFacts> genreFacts = List.of(
            new GenreFacts("Adventure", "medium", List.of("exploration", "puzzles", "story"), "low-medium"),
            new GenreFacts("RPG", "medium", List.of("story", "choices", "progression", "exploration"), "medium-high"),
            new GenreFacts("Cozy", "slow", List.of("relaxation", "collecting", "creativity"), "low"),
            new GenreFacts("Strategy", "slow-medium", List.of("planning", "systems", "optimization"), "high"),
            new GenreFacts("Action", "fast", List.of("reflexes", "combat", "challenge"), "medium")
    );

    private final List<GenreReviews> genreReviews = List.of(
            new GenreReviews("Adventure", List.of("accessible", "narrative-driven", "good exploration"), List.of("can be short", "puzzle pacing varies")),
            new GenreReviews("RPG", List.of("immersive stories", "deep choices", "long-term progression"), List.of("can be complex", "often time-consuming")),
            new GenreReviews("Cozy", List.of("relaxing", "low pressure", "good for short sessions"), List.of("can feel repetitive")),
            new GenreReviews("Strategy", List.of("deep decisions", "high replayability"), List.of("steep learning curve")),
            new GenreReviews("Action", List.of("immediate feedback", "high energy", "skill expression"), List.of("can be stressful", "reaction-heavy"))
    );

    private final List<GameInfo> games = List.of(
            new GameInfo("Outer Wilds", "Adventure", List.of("exploration", "mystery", "story", "relaxed"), List.of("space exploration", "knowledge-based progression", "no combat")),
            new GameInfo("Stardew Valley", "Cozy", List.of("relaxed", "farming", "creativity", "management"), List.of("farming sim", "relationships", "crafting", "open-ended play")),
            new GameInfo("Baldur's Gate 3", "RPG", List.of("story", "choices", "tactics", "fantasy"), List.of("party RPG", "branching story", "tactical combat")),
            new GameInfo("Civilization VI", "Strategy", List.of("planning", "systems", "replayability"), List.of("turn-based empire building", "long sessions", "many viable strategies")),
            new GameInfo("Hades", "Action", List.of("combat", "challenge", "story", "fast"), List.of("fast action", "repeated runs", "strong writing"))
    );

    private final List<GamePrice> prices = List.of(
            new GamePrice("Outer Wilds", new BigDecimal("24.99"), USD),
            new GamePrice("Stardew Valley", new BigDecimal("14.99"), USD),
            new GamePrice("Baldur's Gate 3", new BigDecimal("59.99"), USD),
            new GamePrice("Civilization VI", new BigDecimal("29.99"), USD),
            new GamePrice("Hades", new BigDecimal("24.99"), USD)
    );

    public List<GenreFacts> genreFacts() {
        return genreFacts;
    }

    public List<GenreReviews> genreReviews() {
        return genreReviews;
    }

    public List<GameInfo> games() {
        return games;
    }

    public List<GamePrice> prices() {
        return prices;
    }
}
