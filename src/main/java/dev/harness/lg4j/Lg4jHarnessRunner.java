package dev.harness.lg4j;

import dev.harness.agent.orchestration.RunRequest;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class Lg4jHarnessRunner {

    private static final String PLAN_EXECUTOR_NOT_IMPLEMENTED = "LangGraph4j plan executor is not implemented yet";

    private final Lg4jPlanNode planNode;

    private final Lg4jPlanValidationNode validateNode;

    public Lg4jHarnessRunner(Lg4jPlanNode planNode, Lg4jPlanValidationNode validateNode) {
        this.planNode = planNode;
        this.validateNode = validateNode;
    }

    public RunResult run(RunRequest request) {
        try {
            var finalState = graph().compile().invoke(initialState(request)).orElseThrow(
                    () -> new IllegalStateException("LangGraph4j run graph returned no final state"));
            return failureResult(finalState);
        } catch (Exception exception) {
            return new RunResult(UUID.randomUUID().toString(), request == null ? null : request.sessionId(),
                    RunStatus.FAILED_EXECUTION, null, exception.getMessage(), ErrorClass.FATAL,
                    null, null, List.of(), null);
        }
    }

    private StateGraph<Lg4jRunState> graph() throws GraphStateException {
        return new StateGraph<>(Lg4jRunState.SCHEMA, Lg4jRunState::new)
                .addNode("plan", node_async(planNode::plan))
                .addNode("validate", node_async(validateNode::validate))
                .addNode("execute", node_async(this::execute))
                .addNode("verify", node_async(state -> Map.of()))
                .addNode("finish", node_async(state -> Map.of()))
                .addEdge(START, "plan")
                .addEdge("plan", "validate")
                .addEdge("validate", "execute")
                .addEdge("execute", "verify")
                .addEdge("verify", "finish")
                .addEdge("finish", END);
    }

    private Map<String, Object> execute(Lg4jRunState state) {
        if (state.terminal()) {
            return Map.of();
        }
        return Map.of(
                Lg4jRunState.STATUS, RunStatus.FAILED_EXECUTION,
                Lg4jRunState.ERROR, PLAN_EXECUTOR_NOT_IMPLEMENTED
        );
    }

    private static Map<String, Object> initialState(RunRequest request) {
        var state = new HashMap<String, Object>();
        state.put(Lg4jRunState.GOAL, request == null || request.goal() == null ? "" : request.goal());
        if (request != null && request.sessionId() != null) {
            state.put(Lg4jRunState.SESSION_ID, request.sessionId());
        }
        state.put(Lg4jRunState.RUN_ID, UUID.randomUUID().toString());
        return state;
    }

    private static RunResult failureResult(Lg4jRunState state) {
        return new RunResult(
                state.runId().orElseGet(() -> UUID.randomUUID().toString()),
                state.sessionId().orElse(null),
                state.status().orElse(RunStatus.FAILED_EXECUTION),
                null,
                state.error().orElse(PLAN_EXECUTOR_NOT_IMPLEMENTED),
                state.errorClass().orElse(ErrorClass.FATAL),
                null,
                state.plan().orElse(null),
                List.of(),
                null
        );
    }
}
