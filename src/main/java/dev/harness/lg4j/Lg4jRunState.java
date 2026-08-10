package dev.harness.lg4j;

import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

final class Lg4jRunState extends AgentState {

    static final String GOAL = "goal";
    static final String SESSION_ID = "sessionId";
    static final String RUN_ID = "runId";
    static final String STATUS = "status";
    static final String ERROR = "error";
    static final String RESULT = "result";

    static final Map<String, Channel<?>> SCHEMA = Map.of();

    Lg4jRunState(Map<String, Object> initData) {
        super(initData);
    }

    Optional<String> runId() {
        return value(RUN_ID);
    }

    Optional<String> sessionId() {
        return value(SESSION_ID);
    }

    Optional<RunStatus> status() {
        return value(STATUS);
    }

    Optional<String> error() {
        return value(ERROR);
    }

    Optional<RunResult> result() {
        return value(RESULT);
    }

    boolean terminal() {
        return status().isPresent();
    }

}
