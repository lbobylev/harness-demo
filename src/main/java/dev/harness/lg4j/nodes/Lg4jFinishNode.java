package dev.harness.lg4j.nodes;

import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.lg4j.state.Lg4jRunState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Lg4jFinishNode {

    public static final String MISSING_TERMINAL_STATUS = "run finished without terminal status";

    public Map<String, Object> finish(Lg4jRunState state) {
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
