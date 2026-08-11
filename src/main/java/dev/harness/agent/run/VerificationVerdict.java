package dev.harness.agent.run;

import java.io.Serializable;
import java.util.Map;

public record VerificationVerdict(
        boolean passed,
        String reason,
        Map<String, Object> details
) implements Serializable {

    public VerificationVerdict {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static VerificationVerdict pass() {
        return new VerificationVerdict(true, "passed", Map.of());
    }

    public static VerificationVerdict failed(String reason) {
        return new VerificationVerdict(false, reason, Map.of());
    }

    public static VerificationVerdict failed(String reason, Map<String, Object> details) {
        return new VerificationVerdict(false, reason, details);
    }
}
