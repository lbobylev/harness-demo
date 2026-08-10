package dev.harness.lg4j;

record Lg4jToolSpec(
        String name,
        String parameters,
        String resultType,
        String role,
        boolean terminal
) {

    String promptLine() {
        return "%s(%s) -> %s role=%s terminal=%s"
                .formatted(name, parameters, resultType, role, terminal);
    }
}
