package dev.harness.agent.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecoveryPolicyTests {

    @Test
    void replansForValidationAndMissingInfoFailures() {
        assertThat(RecoveryPolicy.decide(ErrorClass.VALIDATION)).isEqualTo(RecoveryAction.REPLAN);
        assertThat(RecoveryPolicy.decide(ErrorClass.MISSING_INFO)).isEqualTo(RecoveryAction.REPLAN);
    }

    @Test
    void retriesTransientFailures() {
        assertThat(RecoveryPolicy.decide(ErrorClass.TRANSIENT)).isEqualTo(RecoveryAction.RETRY);
    }

    @Test
    void haltsFatalOrUnknownFailures() {
        assertThat(RecoveryPolicy.decide(ErrorClass.FATAL)).isEqualTo(RecoveryAction.HALT);
        assertThat(RecoveryPolicy.decide(null)).isEqualTo(RecoveryAction.HALT);
    }
}
