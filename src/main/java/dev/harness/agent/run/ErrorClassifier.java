package dev.harness.agent.run;

public final class ErrorClassifier {

    private ErrorClassifier() {
    }

    public static ErrorClass classify(HarnessErrorCode code) {
        if (code == null) {
            return ErrorClass.FATAL;
        }

        return switch (code) {
            case UNKNOWN_TOOL, MISSING_REQUIRED_ARGUMENT, INVALID_ARGUMENT -> ErrorClass.VALIDATION;
            case MISSING_INFO -> ErrorClass.MISSING_INFO;
            case RATE_LIMITED, TIMEOUT -> ErrorClass.TRANSIENT;
            case TOOL_EXECUTION_FAILED -> ErrorClass.FATAL;
        };
    }
}
