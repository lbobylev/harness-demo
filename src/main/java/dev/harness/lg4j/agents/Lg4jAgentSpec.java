package dev.harness.lg4j.agents;

record Lg4jAgentSpec(
        String name,
        String arguments,
        String resultType,
        String role,
        boolean terminal
) {

    String promptLine() {
        return "%s(%s) -> %s role=%s terminal=%s"
                .formatted(name, arguments, resultType, role, terminal);
    }
}
