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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

class Lg4jPlanGraphBuilderTests {

    private final Lg4jPlanGraphBuilder builder = new Lg4jPlanGraphBuilder();

    @Test
    void buildsBranchSubgraphsIntoSharedJoin() throws Exception {
        var plan = structuredPlan();

        var state = invoke(plan, structuredShape(), node -> node_async(current -> {
            if ("assemble".equals(node.getId())) {
                assertThat(current.statuses())
                        .containsEntry("compare", NodeStatus.DONE)
                        .containsEntry("signature", NodeStatus.DONE)
                        .containsEntry("traces", NodeStatus.DONE);
            }
            return done(node);
        }));

        assertThat(state.statuses())
                .containsEntry("metrics", NodeStatus.DONE)
                .containsEntry("compare", NodeStatus.DONE)
                .containsEntry("logs", NodeStatus.DONE)
                .containsEntry("signature", NodeStatus.DONE)
                .containsEntry("traces", NodeStatus.DONE)
                .containsEntry("assemble", NodeStatus.DONE)
                .containsEntry("correlate", NodeStatus.DONE)
                .containsEntry("test", NodeStatus.DONE)
                .containsEntry("report", NodeStatus.DONE);
    }

    @Test
    void propagatesBranchFailuresThroughTailDependencies() throws Exception {
        var plan = structuredPlan();

        var state = invoke(plan, structuredShape(), node -> node_async(current -> {
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
                .containsEntry("assemble", NodeStatus.SKIPPED)
                .containsEntry("correlate", NodeStatus.SKIPPED)
                .containsEntry("test", NodeStatus.SKIPPED)
                .containsEntry("report", NodeStatus.SKIPPED);
    }

    @Test
    void failsForUnknownShapeNode() {
        var shape = new Lg4jPlanShape(List.of(List.of("missing")), List.of("report"));
        var plan = new Plan(List.of(node("report", "build_incident_report")));

        assertThatThrownBy(() -> compile(plan, shape, doneAction()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    private Lg4jPlanExecutionState invoke(
            Plan plan,
            Lg4jPlanShape shape,
            java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> action) throws Exception {
        return compile(plan, shape, action)
                .invoke(initialState())
                .orElseThrow(() -> new IllegalStateException("graph returned no final state"));
    }

    private CompiledGraph<Lg4jPlanExecutionState> compile(
            Plan plan,
            Lg4jPlanShape shape,
            java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> action) throws Exception {
        StateGraph<Lg4jPlanExecutionState> graph = builder.build(plan, shape, action);
        return graph.compile();
    }

    private static java.util.function.Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> doneAction() {
        return node -> node_async(state -> done(node));
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

    private static Plan structuredPlan() {
        return new Plan(List.of(
                node("metrics", "query_prometheus"),
                node("compare", "compare_periods", "metrics"),
                node("logs", "query_loki"),
                node("signature", "find_log_signature", "logs"),
                node("traces", "query_tempo"),
                node("assemble", "assemble_evidence", "compare", "signature", "traces"),
                node("correlate", "correlate", "assemble"),
                node("test", "test_hypothesis", "correlate"),
                node("report", "build_incident_report", "test")
        ));
    }

    private static Lg4jPlanShape structuredShape() {
        return new Lg4jPlanShape(
                List.of(List.of("metrics", "compare"), List.of("logs", "signature"), List.of("traces")),
                List.of("assemble", "correlate", "test", "report"));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }
}
