package dev.harness.agent.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorClassifierTests {

    @Test
    void classifiesValidationCodes() {
        assertThat(ErrorClassifier.classify(HarnessErrorCode.UNKNOWN_TOOL)).isEqualTo(ErrorClass.VALIDATION);
        assertThat(ErrorClassifier.classify(HarnessErrorCode.MISSING_REQUIRED_ARGUMENT)).isEqualTo(ErrorClass.VALIDATION);
        assertThat(ErrorClassifier.classify(HarnessErrorCode.INVALID_ARGUMENT)).isEqualTo(ErrorClass.VALIDATION);
    }

    @Test
    void classifiesMissingInfoCodes() {
        assertThat(ErrorClassifier.classify(HarnessErrorCode.MISSING_INFO)).isEqualTo(ErrorClass.MISSING_INFO);
    }

    @Test
    void classifiesTransientCodes() {
        assertThat(ErrorClassifier.classify(HarnessErrorCode.RATE_LIMITED)).isEqualTo(ErrorClass.TRANSIENT);
        assertThat(ErrorClassifier.classify(HarnessErrorCode.TIMEOUT)).isEqualTo(ErrorClass.TRANSIENT);
    }

    @Test
    void classifiesFatalCodes() {
        assertThat(ErrorClassifier.classify(HarnessErrorCode.TOOL_EXECUTION_FAILED)).isEqualTo(ErrorClass.FATAL);
        assertThat(ErrorClassifier.classify(null)).isEqualTo(ErrorClass.FATAL);
    }
}
