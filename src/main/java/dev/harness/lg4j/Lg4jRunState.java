package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

final class Lg4jRunState extends AgentState {

    static final String GOAL = "goal";
    static final String SESSION_ID = "sessionId";
    static final String RUN_ID = "runId";
    static final String PLAN = "plan";
    static final String FAILURE_CONTEXT = "failureContext";
    static final String STATUS = "status";
    static final String ERROR = "error";
    static final String ERROR_CLASS = "errorClass";

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

    Optional<String> goal() {
        return value(GOAL);
    }

    Optional<Plan> plan() {
        return value(PLAN);
    }

    Optional<String> failureContext() {
        return value(FAILURE_CONTEXT);
    }

    Optional<RunStatus> status() {
        return value(STATUS);
    }

    Optional<String> error() {
        return value(ERROR);
    }

    Optional<ErrorClass> errorClass() {
        return value(ERROR_CLASS);
    }

    boolean terminal() {
        return status().isPresent();
    }

}
