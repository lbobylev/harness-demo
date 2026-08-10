package dev.harness.lg4j;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
class Lg4jFinishNode {

    static final String MISSING_TERMINAL_STATUS = "run finished without terminal status";

    Map<String, Object> finish(Lg4jRunState state) {
        if (state.status().isPresent()) {
            return Map.of();
        }
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.ERROR_CLASS, ErrorClass.FATAL,
                Lg4jRunState.ERROR, MISSING_TERMINAL_STATUS
        );
    }
}
