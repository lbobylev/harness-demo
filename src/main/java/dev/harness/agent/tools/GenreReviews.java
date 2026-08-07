package dev.harness.agent.tools;

import java.util.List;

public record GenreReviews(
        String genre,
        List<String> positives,
        List<String> negatives
) {

    public GenreReviews {
        positives = positives == null ? List.of() : List.copyOf(positives);
        negatives = negatives == null ? List.of() : List.copyOf(negatives);
    }
}
