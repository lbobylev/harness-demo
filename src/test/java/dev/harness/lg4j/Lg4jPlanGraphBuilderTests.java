package dev.harness.lg4j;

import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class Lg4jPlanGraphBuilderTests {

    private final Lg4jPlanGraphBuilder builder = new Lg4jPlanGraphBuilder();

    @Test
    void buildsSingleNodeGraph() throws Exception {
        var plan = new Plan(List.of(node("report", "build_incident_report")));

        var state = invoke(plan, doneAction());

        assertThat(state.statuses()).containsEntry("report", NodeStatus.DONE);
    }

    @Test
    void buildsLinearGraph() throws Exception {
        var calls = Collections.synchronizedList(new ArrayList<String>());
        var plan = new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("compare", "compare_periods", "metrics"),
                node("report", "build_incident_report", "compare")
        ));

        var state = invoke(plan, recordingDoneAction(calls));

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("compare", NodeStatus.DONE)
                .containsEntry("report", NodeStatus.DONE);
        assertThat(calls).containsExactly("metrics", "compare", "report");
    }

    @Test
    void buildsFanOutFanInGraph() throws Exception {
        var plan = new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("logs", "query_loki"),
                node("deployments", "get_deployments"),
                node("report", "build_incident_report", "metrics", "logs", "deployments")
        ));

        var state = invoke(plan, node -> node_async(current -> {
            if ("report".equals(node.getId())) {
                assertThat(current.statuses())
                        .containsEntry("metrics", NodeStatus.DONE)
                        .containsEntry("logs", NodeStatus.DONE)
                        .containsEntry("deployments", NodeStatus.DONE);
            }
            return done(node);
        }));

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("deployments", NodeStatus.DONE)
                .containsEntry("report", NodeStatus.DONE);
    }

    @Test
    void buildsDiamondGraph() throws Exception {
        var plan = new Plan(List.of(
                node("root", "query_prometheus"),
                node("left", "query_loki", "root"),
                node("right", "get_deployments", "root"),
                node("report", "build_incident_report", "left", "right")
        ));

        var state = invoke(plan, node -> node_async(current -> {
            if ("report".equals(node.getId())) {
                assertThat(current.statuses())
                        .containsEntry("left", NodeStatus.DONE)
                        .containsEntry("right", NodeStatus.DONE);
            }
            return done(node);
        }));

        assertThat(state.statuses())
                .containsEntry("root", NodeStatus.DONE)
                .containsEntry("left", NodeStatus.DONE)
                .containsEntry("right", NodeStatus.DONE)
                .containsEntry("report", NodeStatus.DONE);
    }

    @Test
    void failsForMultipleTerminalBranches() {
        var plan = new Plan(List.of(
                node("a", "a"),
                node("b", "b", "a"),
                node("c", "c"),
                node("d", "d", "c")
        ));

        assertThatThrownBy(() -> compile(plan, doneAction()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one terminal node");
    }

    @Test
    void propagatesNodeActionFailuresIntoState() throws Exception {
        var plan = new Plan(List.of(
                node("a", "a"),
                node("b", "b", "a")
        ));

        var state = invoke(plan, node -> node_async(current -> {
            if ("a".equals(node.getId())) {
                return Map.of(
                        Lg4jPlanExecutionState.STATUSES, Map.of("a", NodeStatus.FAILED),
                        Lg4jPlanExecutionState.ERRORS, Map.of("a", "boom")
                );
            }
            if (current.statuses().get("a") == NodeStatus.FAILED) {
                return Map.of(
                        Lg4jPlanExecutionState.STATUSES, Map.of("b", NodeStatus.SKIPPED),
                        Lg4jPlanExecutionState.ERRORS, Map.of("b", "dependency failed")
                );
            }
            return done(node);
        }));

        assertThat(state.statuses())
                .containsEntry("a", NodeStatus.FAILED)
                .containsEntry("b", NodeStatus.SKIPPED);
        assertThat(state.errors())
                .containsEntry("a", "boom")
                .containsEntry("b", "dependency failed");
    }

    @Test
    void failsForMissingDependency() {
        var plan = new Plan(List.of(node("report", "build_incident_report", "missing")));

        assertThatThrownBy(() -> compile(plan, doneAction()))
                .hasMessageContaining("missing");
    }

    @Test
    void failsWhenPlanHasNoRootNodes() {
        var plan = new Plan(List.of(
                node("a", "a", "b"),
                node("b", "b", "a")
        ));

        assertThatThrownBy(() -> compile(plan, doneAction()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one root node");
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
        StateGraph<Lg4jPlanExecutionState> graph = builder.build(plan, action);
        return graph.compile();
    }

    private static java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> doneAction() {
        return node -> node_async(state -> done(node));
    }

    private static java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> recordingDoneAction(
            List<String> calls) {
        return node -> node_async(state -> {
            calls.add(node.getId());
            return done(node);
        });
    }

    private static Map<String, Object> done(PlanNode node) {
        return Map.of(Lg4jPlanExecutionState.STATUSES, Map.of(node.getId(), NodeStatus.DONE));
    }

    private static Map<String, Object> initialState() {
        return Map.of(
                Lg4jPlanExecutionState.RESULTS, Map.of(),
                Lg4jPlanExecutionState.STATUSES, Map.of(),
                Lg4jPlanExecutionState.ERRORS, Map.of()
        );
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }
}
