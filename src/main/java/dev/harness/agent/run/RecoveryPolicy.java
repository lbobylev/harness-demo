package dev.harness.agent.run;

public final class RecoveryPolicy {

    private RecoveryPolicy() {
    }

    public static RecoveryAction decide(ErrorClass errorClass) {
        if (errorClass == null) {
            return RecoveryAction.HALT;
        }

        return switch (errorClass) {
            case VALIDATION, MISSING_INFO -> RecoveryAction.REPLAN;
            case TRANSIENT -> RecoveryAction.RETRY;
            case FATAL -> RecoveryAction.HALT;
        };
    }
}
