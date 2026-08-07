package dev.harness.agent.tools;

import java.util.List;

public record GenreFacts(
        String genre,
        String pace,
        List<String> focus,
        String complexity
) {

    public GenreFacts {
        focus = focus == null ? List.of() : List.copyOf(focus);
    }
}
