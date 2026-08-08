package dev.harness.agent.execution;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.HarnessErrorCode;
import dev.harness.agent.tools.ToolExecutor;
import dev.harness.agent.tools.ToolExecutionException;
import dev.harness.agent.tools.ToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DagSchedulerTests {

    @Test
    void executesIndependentNodesConcurrently() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        ToolExecutor toolExecutor = (name, args) -> {
            int running = active.incrementAndGet();
            maxActive.updateAndGet(current -> Math.max(current, running));
            bothStarted.countDown();
            try {
                if (!bothStarted.await(1, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("nodes did not execute concurrently");
                }
                return ToolExecutionResult.of(name);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } finally {
                active.decrementAndGet();
            }
        };
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("games", "get_games")
        ));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget());

        assertThat(result.successful()).isTrue();
        assertThat(maxActive).hasValue(2);
    }

    @Test
    void preservesDependencyOrdering() {
        List<String> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        ToolExecutor toolExecutor = (name, args) -> {
            calls.add(name);
            return ToolExecutionResult.of(name + " result");
        };
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("games", "get_games", "facts")
        ));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget());

        assertThat(result.successful()).isTrue();
        assertThat(calls).containsExactly("get_genre_facts", "get_games");
    }

    @Test
    void schedulesReadyDependentsWithoutWaitingForUnrelatedSlowBranch() {
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch cStarted = new CountDownLatch(1);
        AtomicBoolean bFinished = new AtomicBoolean();
        AtomicReference<Boolean> bFinishedWhenCStarted = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor toolExecutor = (name, args) -> {
            calls.incrementAndGet();
            if ("a".equals(name)) {
                if (!await(bStarted)) {
                    throw new IllegalStateException("b did not start");
                }
                return ToolExecutionResult.of("a");
            }
            if ("b".equals(name)) {
                bStarted.countDown();
                if (!await(cStarted)) {
                    throw new IllegalStateException("c did not start before b finished");
                }
                bFinished.set(true);
                return ToolExecutionResult.of("b");
            }
            if ("c".equals(name)) {
                bFinishedWhenCStarted.set(bFinished.get());
                cStarted.countDown();
                return ToolExecutionResult.of("c");
            }
            return ToolExecutionResult.of(name);
        };
        Plan plan = new Plan(List.of(
                node("a", "a"),
                node("b", "b"),
                node("c", "c", "a"),
                node("d", "d", "b")
        ));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget());

        assertThat(result.successful()).isTrue();
        assertThat(calls).hasValue(4);
        assertThat(bFinishedWhenCStarted.get()).isFalse();
    }

    @Test
    void chargesBudgetForEveryAttemptedToolExecution() {
        Budget budget = budget();
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("games", "get_games")
        ));

        new DagScheduler((name, args) -> ToolExecutionResult.of(name), 2).execute(plan, budget);

        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(2);
    }

    @Test
    void doesNotExecuteMoreReadyNodesThanRemainingToolCallBudget() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                2,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));
        AtomicInteger calls = new AtomicInteger();
        Plan plan = new Plan(List.of(
                node("a", "tool_a"),
                node("b", "tool_b"),
                node("c", "tool_c"),
                node("d", "tool_d")
        ));

        DagExecutionResult result = new DagScheduler((name, args) -> {
            calls.incrementAndGet();
            return ToolExecutionResult.of(name);
        }, 4).execute(plan, budget);

        assertThat(result.successful()).isFalse();
        assertThat(calls).hasValue(2);
        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(2);
        assertThat(plan.nodes()).filteredOn(PlanNode::isDone).hasSize(2);
        assertThat(plan.nodes()).filteredOn(PlanNode::isSkipped).hasSize(2)
                .allSatisfy(node -> assertThat(node.getError()).isEqualTo("budget exhausted"));
    }

    @Test
    void propagatesBudgetSkippedRootsToDependents() {
        Budget budget = new Budget(new BudgetLimits(
                100,
                1,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));
        AtomicInteger calls = new AtomicInteger();
        Plan plan = new Plan(List.of(
                node("a", "a"),
                node("b", "b"),
                node("c", "c", "a"),
                node("d", "d", "b")
        ));

        DagExecutionResult result = new DagScheduler((name, args) -> {
            calls.incrementAndGet();
            return ToolExecutionResult.of(name);
        }, 2).execute(plan, budget);

        assertThat(result.successful()).isFalse();
        assertThat(calls).hasValue(1);
        assertThat(budget.snapshot().toolCallsUsed()).isEqualTo(1);
        assertThat(plan.nodes()).filteredOn(PlanNode::isDone).hasSize(1);
        assertThat(plan.nodes()).filteredOn(PlanNode::isSkipped).hasSize(3);
        assertThat(plan.nodes()).filteredOn(node -> node.isSkipped() && "budget exhausted".equals(node.getError()))
                .isNotEmpty();
        assertThat(plan.nodes()).filteredOn(node -> node.isSkipped() && "dependency failed".equals(node.getError()))
                .isNotEmpty();
    }

    @Test
    void chargesBudgetForToolAiUsage() {
        Budget budget = budgetWithPricing();
        PlanNode facts = node("facts", "get_genre_facts");
        Plan plan = new Plan(List.of(facts));

        new DagScheduler((name, args) -> new ToolExecutionResult("facts", new AiUsage("test-model", 10, 5, 15)), 2)
                .execute(plan, budget);

        assertThat(facts.getUsage()).isEqualTo(new AiUsage("test-model", 10, 5, 15));
        assertThat(budget.snapshot().tokensUsed()).isEqualTo(15);
        assertThat(budget.snapshot().estimatedCostUsd()).isEqualByComparingTo("0.20");
    }

    @Test
    void doesNotScheduleNextLevelAfterToolUsageExhaustsBudget() {
        Budget budget = budgetWithPricing(10);
        AtomicInteger calls = new AtomicInteger();
        ToolExecutor toolExecutor = (name, args) -> {
            calls.incrementAndGet();
            if ("facts".equals(name)) {
                return new ToolExecutionResult("facts", new AiUsage("test-model", 10, 1, 11));
            }
            return ToolExecutionResult.of("summary");
        };
        PlanNode facts = node("facts", "facts");
        PlanNode summary = node("summary", "summary", "facts");
        Plan plan = new Plan(List.of(facts, summary));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget);

        assertThat(result.successful()).isFalse();
        assertThat(calls).hasValue(1);
        assertThat(facts.getStatus()).isEqualTo(NodeStatus.DONE);
        assertThat(summary.getStatus()).isEqualTo(NodeStatus.SKIPPED);
        assertThat(summary.getError()).isEqualTo("budget exhausted");
    }

    @Test
    void skipsDependentNodesWhenPrerequisiteFails() {
        ToolExecutor toolExecutor = (name, args) -> {
            if ("get_genre_facts".equals(name)) {
                throw new IllegalStateException("facts unavailable");
            }
            return ToolExecutionResult.of(name);
        };
        PlanNode facts = node("facts", "get_genre_facts");
        PlanNode games = node("games", "get_games", "facts");
        Plan plan = new Plan(List.of(facts, games));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget());

        assertThat(result.successful()).isFalse();
        assertThat(facts.getStatus()).isEqualTo(NodeStatus.FAILED);
        assertThat(facts.getError()).contains("facts unavailable");
        assertThat(games.getStatus()).isEqualTo(NodeStatus.SKIPPED);
        assertThat(games.getError()).isEqualTo("dependency failed");
    }

    @Test
    void storesStrictErrorCodeFromToolExecutionException() {
        ToolExecutor toolExecutor = (name, args) -> {
            throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
        };
        PlanNode facts = node("facts", "get_genre_facts");
        Plan plan = new Plan(List.of(facts));

        DagExecutionResult result = new DagScheduler(toolExecutor, 2).execute(plan, budget());

        assertThat(result.successful()).isFalse();
        assertThat(facts.getStatus()).isEqualTo(NodeStatus.FAILED);
        assertThat(facts.getErrorCode()).isEqualTo(HarnessErrorCode.MISSING_INFO);
    }

    @Test
    void notifiesListenerForSuccessfulNodeExecution() {
        List<NodeEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        PlanNode facts = node("facts", "get_genre_facts");
        Plan plan = new Plan(List.of(facts));

        DagExecutionResult result = new DagScheduler((name, args) -> ToolExecutionResult.of(List.of("facts")), 2)
                .execute(plan, budget(), (node, kind, latency, currentBudget) -> events.add(new NodeEvent(
                        kind,
                        node.getId(),
                        node.getStatus(),
                        latency,
                        currentBudget.snapshot().toolCallsUsed(),
                        node.getErrorCode())));

        assertThat(result.successful()).isTrue();
        assertThat(events)
                .extracting(NodeEvent::kind)
                .containsExactly("node.start", "node.finish");
        assertThat(events.get(0).status()).isEqualTo(NodeStatus.RUNNING);
        assertThat(events.get(0).toolCallsUsed()).isEqualTo(1);
        assertThat(events.get(1).status()).isEqualTo(NodeStatus.DONE);
        assertThat(events.get(1).latency()).isNotNull();
    }

    @Test
    void notifiesListenerForFailedNodeExecution() {
        List<NodeEvent> events = java.util.Collections.synchronizedList(new ArrayList<>());
        PlanNode facts = node("facts", "get_genre_facts");
        Plan plan = new Plan(List.of(facts));

        DagExecutionResult result = new DagScheduler((name, args) -> {
            throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
        }, 2).execute(plan, budget(), (node, kind, latency, currentBudget) -> events.add(new NodeEvent(
                kind,
                node.getId(),
                node.getStatus(),
                latency,
                currentBudget.snapshot().toolCallsUsed(),
                node.getErrorCode())));

        assertThat(result.successful()).isFalse();
        assertThat(events)
                .extracting(NodeEvent::kind)
                .containsExactly("node.start", "node.fail");
        assertThat(events.get(1).status()).isEqualTo(NodeStatus.FAILED);
        assertThat(events.get(1).latency()).isNotNull();
        assertThat(events.get(1).errorCode()).isEqualTo(HarnessErrorCode.MISSING_INFO);
    }

    @Test
    void finalSynthesisReceivesDependencyResults() {
        AtomicReference<Map<String, Object>> summaryArgs = new AtomicReference<>();
        ToolExecutor toolExecutor = (name, args) -> switch (name) {
            case "get_genre_facts" -> ToolExecutionResult.of(List.of("facts"));
            case "get_genre_reviews" -> ToolExecutionResult.of(List.of("reviews"));
            case "get_games" -> ToolExecutionResult.of(List.of("games"));
            case "get_prices" -> ToolExecutionResult.of(List.of("prices"));
            case "summarizer_node" -> {
                summaryArgs.set(new LinkedHashMap<>(args));
                yield ToolExecutionResult.of("summary");
            }
            default -> throw new IllegalArgumentException(name);
        };
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("reviews", "get_genre_reviews"),
                node("games", "get_games"),
                node("prices", "get_prices"),
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("preferences", "cozy exploration"),
                        nodeResult("genreFacts", "facts"),
                        nodeResult("genreReviews", "reviews"),
                        nodeResult("games", "games"),
                        nodeResult("prices", "prices")
                ), List.of("facts", "reviews", "games", "prices"))
        ));

        DagExecutionResult result = new DagScheduler(toolExecutor, 4)
                .execute(plan, budget());

        assertThat(result.successful()).isTrue();
        assertThat(summaryArgs.get())
                .containsEntry("preferences", "cozy exploration")
                .containsEntry("genreFacts", List.of("facts"))
                .containsEntry("genreReviews", List.of("reviews"))
                .containsEntry("games", List.of("games"))
                .containsEntry("prices", List.of("prices"));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private static ArgumentBinding literal(String argumentName, String value) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding nodeResult(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }

    private static Budget budget() {
        return new Budget(new BudgetLimits(
                100,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ));
    }

    private static Budget budgetWithPricing() {
        return budgetWithPricing(100);
    }

    private static Budget budgetWithPricing(long maxTokens) {
        return new Budget(new BudgetLimits(
                maxTokens,
                10,
                Duration.ofMinutes(1),
                new BigDecimal("1.00")
        ), new ModelPricing("test-model", new BigDecimal("0.01"), new BigDecimal("0.02")));
    }

    private record NodeEvent(
            String kind,
            String nodeId,
            NodeStatus status,
            Duration latency,
            long toolCallsUsed,
            HarnessErrorCode errorCode) {
    }
}
