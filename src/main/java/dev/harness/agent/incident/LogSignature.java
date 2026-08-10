package dev.harness.agent.incident;

import java.io.Serializable;
import java.util.List;

public record LogSignature(
        String signature,
        String firstSeen,
        int count,
        String level,
        String service,
        List<String> evidenceIds
) implements Serializable {
    public LogSignature {
        evidenceIds = evidenceIds == null ? List.of() : List.copyOf(evidenceIds);
    }
}
