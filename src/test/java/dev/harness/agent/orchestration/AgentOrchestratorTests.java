package dev.harness.agent.orchestration;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.execution.DagScheduler;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.planning.Planner;
import dev.harness.agent.planning.PlanningResult;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.HarnessErrorCode;
import dev.harness.agent.run.RecoveryAction;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.tools.GameRecommendationData;
import dev.harness.agent.tools.GameRecommendationTools;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolExecutor;
import dev.harness.agent.tools.ToolExecutionException;
import dev.harness.agent.tools.ToolExecutionResult;
import dev.harness.agent.validation.DagValidator;
import dev.harness.agent.verification.FinalReport;
import dev.harness.agent.verification.ReportVerifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentOrchestratorTests {

    @Test
    void returnsSucceededRunWithReportAndTrace() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), AiUsage.none()),
                (name, args) -> switch (name) {
                    case "get_genre_facts" -> ToolExecutionResult.of(List.of("facts"));
                    case "get_genre_reviews" -> ToolExecutionResult.of(List.of("reviews"));
                    case "get_games" -> ToolExecutionResult.of(List.of("games"));
                    case "get_prices" -> ToolExecutionResult.of(List.of("prices"));
                    case "summarizer_node" -> ToolExecutionResult.of(new TestReport("final report"));
                    default -> throw new IllegalArgumentException(name);
                },
                100,
                10,
                0);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.report()).isEqualTo("final report");
        assertThat(result.sessionId()).isEqualTo("session-1");
        assertThat(result.budget()).isNotNull();
        assertThat(result.traceEvents())
                .extracting(event -> event.kind())
                .contains("run.start", "planning.finish", "validation.finish", "execution.finish", "verification.finish", "run.finish");
        assertThat(result.traceEvents())
                .filteredOn(event -> "node.finish".equals(event.kind()) && "summary".equals(event.nodeId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.role()).isEqualTo("FINAL_SYNTHESIS");
                    assertThat(event.latencyMs()).isNotNull();
                    assertThat(event.budget()).isNotNull();
                    assertThat(event.data()).containsEntry("tool", "summarizer_node");
                });
        assertThat(result.traceEvents())
                .allSatisfy(event -> assertThat(event.budget()).isNotNull());
    }

    @Test
    void returnsValidationFailureForInvalidPlan() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(new Plan(List.of(node("facts", "get_genre_facts"))), AiUsage.none()),
                (name, args) -> ToolExecutionResult.of(name),
                100,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_VALIDATION);
        assertThat(result.error()).contains("FINAL_SYNTHESIS");
    }

    @Test
    void returnsExecutionFailureWhenExecutorCannotCompletePlan() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), AiUsage.none()),
                (name, args) -> {
                    if ("get_genre_facts".equals(name)) {
                        throw new IllegalStateException("tool unavailable");
                    }
                    return ToolExecutionResult.of(new TestReport("final report"));
                },
                100,
                10,
                0);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_EXECUTION);
        assertThat(result.error()).contains("execution failed");
    }

    @Test
    void returnsClassifiedExecutionFailure() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), AiUsage.none()),
                (name, args) -> {
                    if ("get_genre_facts".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    return ToolExecutionResult.of(new TestReport("final report"));
                },
                100,
                10,
                0);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_EXECUTION);
        assertThat(result.errorClass()).isEqualTo(ErrorClass.MISSING_INFO);
        assertThat(result.traceEvents())
                .filteredOn(event -> "recovery.decide".equals(event.kind()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.status()).isEqualTo(RecoveryAction.REPLAN.name());
                    assertThat(event.data()).containsEntry("errorClass", ErrorClass.MISSING_INFO.name());
                });
        assertThat(result.traceEvents())
                .filteredOn(event -> "node.fail".equals(event.kind()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.nodeId()).isEqualTo("facts");
                    assertThat(event.latencyMs()).isNotNull();
                    assertThat(event.message()).isEqualTo("missing info");
                    assertThat(event.data()).containsEntry("errorCode", HarnessErrorCode.MISSING_INFO.name());
                });
    }

    @Test
    void recordsRetryRecoveryDecisionForTransientExecutionFailure() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), AiUsage.none()),
                (name, args) -> {
                    if ("get_genre_facts".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.TIMEOUT, "timeout");
                    }
                    return ToolExecutionResult.of(new TestReport("final report"));
                },
                100,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_EXECUTION);
        assertThat(result.errorClass()).isEqualTo(ErrorClass.TRANSIENT);
        assertThat(result.traceEvents())
                .filteredOn(event -> "recovery.decide".equals(event.kind()))
                .singleElement()
                .satisfies(event -> assertThat(event.status()).isEqualTo(RecoveryAction.RETRY.name()));
    }

    @Test
    void replansAfterMissingInfoExecutionFailure() {
        AtomicInteger plannerCalls = new AtomicInteger();
        AtomicInteger factsCalls = new AtomicInteger();
        AtomicReference<String> replanFailureContext = new AtomicReference<>();
        AgentOrchestrator orchestrator = orchestrator(
                request -> {
                    if (plannerCalls.incrementAndGet() == 2) {
                        replanFailureContext.set(request.failureContext());
                    }
                    return new PlanningResult(validPlan(), AiUsage.none());
                },
                (name, args) -> {
                    if ("get_genre_facts".equals(name) && factsCalls.incrementAndGet() == 1) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    if ("get_genre_facts".equals(name)) {
                        return ToolExecutionResult.of(List.of("facts"));
                    }
                    return ToolExecutionResult.of(new TestReport("final report"));
                },
                100,
                10,
                1);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.report()).isEqualTo("final report");
        assertThat(plannerCalls).hasValue(2);
        assertThat(replanFailureContext.get()).contains("MISSING_INFO", "get_genre_facts");
        assertThat(result.traceEvents())
                .filteredOn(event -> "replanning.start".equals(event.kind()))
                .hasSize(1);
    }

    @Test
    void doesNotReplanPastConfiguredLimit() {
        AtomicInteger plannerCalls = new AtomicInteger();
        AgentOrchestrator orchestrator = orchestrator(
                request -> {
                    plannerCalls.incrementAndGet();
                    return new PlanningResult(validPlan(), AiUsage.none());
                },
                (name, args) -> {
                    if ("get_genre_facts".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    return ToolExecutionResult.of(new TestReport("final report"));
                },
                100,
                10,
                1);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_EXECUTION);
        assertThat(result.errorClass()).isEqualTo(ErrorClass.MISSING_INFO);
        assertThat(plannerCalls).hasValue(2);
        assertThat(result.traceEvents())
                .filteredOn(event -> "replanning.start".equals(event.kind()))
                .hasSize(1);
    }

    @Test
    void returnsVerificationFailureWhenFinalResultDoesNotImplementFinalReport() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), AiUsage.none()),
                (name, args) -> switch (name) {
                    case "get_genre_facts" -> ToolExecutionResult.of(List.of("facts"));
                    case "get_genre_reviews" -> ToolExecutionResult.of(List.of("reviews"));
                    case "get_games" -> ToolExecutionResult.of(List.of("games"));
                    case "get_prices" -> ToolExecutionResult.of(List.of("prices"));
                    case "summarizer_node" -> ToolExecutionResult.of("plain string");
                    default -> throw new IllegalArgumentException(name);
                },
                100,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_VERIFICATION);
        assertThat(result.error()).contains("FinalReport");
    }

    @Test
    void returnsBudgetExhaustedAfterPlanningTokenCharge() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(validPlan(), new AiUsage("test-model", 2, 0, 2)),
                (name, args) -> ToolExecutionResult.of(name),
                1,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.BUDGET_EXHAUSTED);
        assertThat(result.error()).contains("after planning");
    }

    @Test
    void returnsPlanningFailureWhenPlannerThrows() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> {
                    throw new IllegalStateException("planner down");
                },
                (name, args) -> ToolExecutionResult.of(name),
                100,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_PLANNING);
        assertThat(result.error()).contains("planner down");
    }

    private static AgentOrchestrator orchestrator(
            Planner planner,
            ToolExecutor toolExecutor,
            long maxTokens,
            long maxToolCalls) {
        return orchestrator(planner, toolExecutor, maxTokens, maxToolCalls, 1);
    }

    private static AgentOrchestrator orchestrator(
            Planner planner,
            ToolExecutor toolExecutor,
            long maxTokens,
            long maxToolCalls,
            int maxReplans) {
        GameRecommendationTools tools = new GameRecommendationTools(new GameRecommendationData(), null, new AiUsageExtractor());
        ToolCatalog toolCatalog = new ToolCatalog(tools);
        return new AgentOrchestrator(
                planner,
                new DagValidator(toolCatalog),
                new DagScheduler(toolExecutor, 2),
                new ReportVerifier(toolCatalog),
                toolCatalog,
                maxTokens,
                maxToolCalls,
                Duration.ofMinutes(1),
                new BigDecimal("1.00"),
                "test-model",
                new BigDecimal("1.00"),
                BigDecimal.ZERO,
                maxReplans);
    }

    private static Plan validPlan() {
        return new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("reviews", "get_genre_reviews"),
                node("games", "get_games"),
                node("prices", "get_prices"),
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("preferences", "goal"),
                        nodeResult("genreFacts", "facts"),
                        nodeResult("genreReviews", "reviews"),
                        nodeResult("games", "games"),
                        nodeResult("prices", "prices")
                ), List.of("facts", "reviews", "games", "prices"))
        ));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static ArgumentBinding literal(String argumentName, String value) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding nodeResult(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }

    private record TestReport(String reportText) implements FinalReport {
    }
}
