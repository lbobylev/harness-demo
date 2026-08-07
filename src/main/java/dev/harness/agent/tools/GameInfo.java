package dev.harness.agent.tools;

import java.util.List;

public record GameInfo(
        String title,
        String genre,
        List<String> tags,
        List<String> facts
) {

    public GameInfo {
        tags = tags == null ? List.of() : List.copyOf(tags);
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
