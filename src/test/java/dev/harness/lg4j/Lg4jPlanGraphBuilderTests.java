package dev.harness.lg4j;

import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class Lg4jPlanGraphBuilderTests {

    private final Lg4jPlanGraphBuilder builder = new Lg4jPlanGraphBuilder();

    @Test
    void buildsEvidenceDagIntoSharedJoin() throws Exception {
        var plan = structuredPlan();

        var state = invoke(plan, doneAction());

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("compare", NodeStatus.DONE)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("signature", NodeStatus.DONE)
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, NodeStatus.DONE);
    }

    @Test
    void propagatesEvidenceDagFailuresThroughTailDependencies() throws Exception {
        var plan = structuredPlan();

        var state = invoke(plan, node -> node_async(current -> {
            if ("metrics".equals(node.getId())) {
                return Map.of(
                        Lg4jPlanExecutionState.STATUSES, Map.of("metrics", NodeStatus.FAILED),
                        Lg4jPlanExecutionState.ERRORS, Map.of("metrics", "boom")
                );
            }
            if (node.getDeps().stream().anyMatch(dep -> current.statuses().get(dep) == NodeStatus.FAILED
                    || current.statuses().get(dep) == NodeStatus.SKIPPED)) {
                return Map.of(
                        Lg4jPlanExecutionState.STATUSES, Map.of(node.getId(), NodeStatus.SKIPPED),
                        Lg4jPlanExecutionState.ERRORS, Map.of(node.getId(), "dependency failed")
                );
            }
            return done(node);
        }));

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.FAILED)
                .containsEntry("compare", NodeStatus.SKIPPED)
                .containsEntry(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, NodeStatus.SKIPPED);
    }

    @Test
    void supportsFanInWithinEvidenceDag() throws Exception {
        var plan = new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("logs", "query_loki"),
                node("mixed", "compare_periods", "metrics", "logs")));

        var state = invoke(plan, doneAction());

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("mixed", NodeStatus.DONE)
                .containsEntry(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, NodeStatus.DONE);
    }

    private Lg4jPlanExecutionState invoke(
            Plan plan,
            java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> action) throws Exception {
        return compile(plan, action)
                .invoke(initialState())
                .orElseThrow(() -> new IllegalStateException("graph returned no final state"));
    }

    private CompiledGraph<Lg4jPlanExecutionState> compile(
            Plan plan,
            java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> action) throws Exception {
        StateGraph<Lg4jPlanExecutionState> graph = builder.build(plan, action,
                node_async(state -> {
                    if (state.statuses().containsValue(NodeStatus.FAILED)
                            || state.statuses().containsValue(NodeStatus.SKIPPED)) {
                        return Map.of(
                                Lg4jPlanExecutionState.STATUSES,
                                Map.of(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE, NodeStatus.SKIPPED));
                    }
                    assertThat(plan.nodes())
                            .extracting(PlanNode::getId)
                            .allSatisfy(nodeId -> assertThat(state.statuses()).containsEntry(nodeId, NodeStatus.DONE));
                    return done(Lg4jPlanGraphBuilder.ANALYZE_EVIDENCE);
                }));
        return graph.compile();
    }

    private static java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> doneAction() {
        return node -> node_async(state -> done(node));
    }

    private static Map<String, Object> done(PlanNode node) {
        return done(node.getId());
    }

    private static Map<String, Object> done(String nodeId) {
        return Map.of(Lg4jPlanExecutionState.STATUSES, Map.of(nodeId, NodeStatus.DONE));
    }

    private static Map<String, Object> initialState() {
        return Map.of(
                Lg4jPlanExecutionState.RESULTS, Map.of(),
                Lg4jPlanExecutionState.STATUSES, Map.of(),
                Lg4jPlanExecutionState.ERRORS, Map.of()
        );
    }

    private static Plan structuredPlan() {
        return new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("compare", "compare_periods", "metrics"),
                node("logs", "query_loki"),
                node("signature", "find_log_signature", "logs"),
                node("traces", "query_tempo")
        ));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }
}
