package dev.harness.lg4j;

import dev.harness.agent.orchestration.RunRequest;
import dev.harness.agent.budget.Budget;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Component
public class Lg4jHarnessRunner {

    private static final Logger log = LoggerFactory.getLogger(Lg4jHarnessRunner.class);

    private final Lg4jPlanNode planNode;

    private final Lg4jBudgetFactory budgetFactory;

    private final Lg4jPlanValidationNode validateNode;

    private final Lg4jPlanExecutionNode executionNode;

    private final Lg4jReportNode reportNode;

    private final Lg4jVerificationNode verificationNode;

    private final Lg4jReplanDecisionNode replanDecisionNode;

    private final Lg4jFinishNode finishNode;

    public Lg4jHarnessRunner(
            Lg4jBudgetFactory budgetFactory,
            Lg4jPlanNode planNode,
            Lg4jPlanValidationNode validateNode,
            Lg4jPlanExecutionNode executionNode,
            Lg4jReportNode reportNode,
            Lg4jVerificationNode verificationNode,
            Lg4jReplanDecisionNode replanDecisionNode,
            Lg4jFinishNode finishNode) {
        this.budgetFactory = budgetFactory;
        this.planNode = planNode;
        this.validateNode = validateNode;
        this.executionNode = executionNode;
        this.reportNode = reportNode;
        this.verificationNode = verificationNode;
        this.replanDecisionNode = replanDecisionNode;
        this.finishNode = finishNode;
    }

    public RunResult run(RunRequest request) {
        try {
            var budget = budgetFactory.create();
            var finalState = graph(budget).compile().invoke(initialState(request, budget)).orElseThrow(
                    () -> new IllegalStateException("LangGraph4j run graph returned no final state"));
            return result(finalState);
        } catch (Exception exception) {
            log.warn("LangGraph4j run failed: {}", Lg4jDebugValue.dump(exception), exception);
            return new RunResult(UUID.randomUUID().toString(), request == null ? null : request.sessionId(),
                    RunStatus.FAILED_EXECUTION, null, Lg4jDebugValue.dump(exception), ErrorClass.FATAL,
                    null, null, List.of(), null);
        }
    }

    private StateGraph<Lg4jRunState> graph(Budget budget) throws GraphStateException {
        return new StateGraph<>(Lg4jRunState.SCHEMA, Lg4jRunState::new)
                .addNode("plan", node_async(state -> planNode.plan(state, budget)))
                .addNode("validate", node_async(validateNode::validate))
                .addNode("execute", node_async(state -> executionNode.execute(state, budget)))
                .addNode("build_report", node_async(reportNode::build))
                .addNode("verify", node_async(verificationNode::verify))
                .addNode("decide_replan", node_async(state -> replanDecisionNode.decide(state, budget)))
                .addNode("finish", node_async(finishNode::finish))
                .addEdge(START, "plan")
                .addEdge("plan", "validate")
                .addEdge("validate", "execute")
                .addEdge("execute", "build_report")
                .addEdge("build_report", "verify")
                .addEdge("verify", "decide_replan")
                .addConditionalEdges("decide_replan",
                        edge_async(state -> state.needsReplan() ? "replan" : "finish"),
                        Map.of("replan", "plan", "finish", "finish"))
                .addEdge("finish", END);
    }

    private Map<String, Object> initialState(RunRequest request, Budget budget) {
        var state = new HashMap<String, Object>();
        state.put(Lg4jRunState.GOAL, request == null || request.goal() == null ? "" : request.goal());
        if (request != null && request.sessionId() != null) {
            state.put(Lg4jRunState.SESSION_ID, request.sessionId());
        }
        state.put(Lg4jRunState.RUN_ID, UUID.randomUUID().toString());
        state.put(Lg4jRunState.BUDGET, budget.snapshot());
        return state;
    }

    private static RunResult result(Lg4jRunState state) {
        return new RunResult(
                state.runId().orElseGet(() -> UUID.randomUUID().toString()),
                state.sessionId().orElse(null),
                state.status().orElse(RunStatus.FAILED_EXECUTION),
                state.report().orElse(null),
                state.error().orElse(Lg4jFinishNode.MISSING_TERMINAL_STATUS),
                state.errorClass().orElse(ErrorClass.FATAL),
                state.verdict().orElse(null),
                state.plan().orElse(null),
                List.of(),
                state.budget().orElse(null)
        );
    }
}
