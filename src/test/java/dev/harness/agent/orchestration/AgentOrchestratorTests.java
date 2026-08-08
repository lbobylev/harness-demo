package dev.harness.agent.orchestration;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.execution.DagScheduler;
import dev.harness.agent.incident.IncidentData;
import dev.harness.agent.incident.IncidentReport;
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
import dev.harness.agent.tools.IncidentInvestigationTools;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolExecutor;
import dev.harness.agent.tools.ToolExecutionException;
import dev.harness.agent.tools.ToolExecutionResult;
import dev.harness.agent.validation.DagValidator;
import dev.harness.agent.validation.IncidentPolicyValidator;
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
                    case "query_loki" -> ToolExecutionResult.of(List.of("logs"));
                    case "find_log_signature" -> ToolExecutionResult.of(List.of("signature"));
                    case "test_hypothesis" -> ToolExecutionResult.of(List.of("hypothesis"));
                    case "build_incident_report" -> ToolExecutionResult.of(validReport());
                    default -> throw new IllegalArgumentException(name);
                },
                100,
                10,
                0);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.report()).contains("catalog-service degradation");
        assertThat(result.sessionId()).isEqualTo("session-1");
        assertThat(result.budget()).isNotNull();
        assertThat(result.traceEvents())
                .extracting(event -> event.kind())
                .contains("run.start", "planning.finish", "validation.finish", "execution.finish", "verification.finish", "run.finish");
        assertThat(result.traceEvents())
                .filteredOn(event -> "node.finish".equals(event.kind()) && "report".equals(event.nodeId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.role()).isEqualTo("FINAL_SYNTHESIS");
                    assertThat(event.latencyMs()).isNotNull();
                    assertThat(event.budget()).isNotNull();
                    assertThat(event.data()).containsEntry("tool", "build_incident_report");
                });
        assertThat(result.traceEvents())
                .allSatisfy(event -> assertThat(event.budget()).isNotNull());
    }

    @Test
    void returnsValidationFailureForInvalidPlan() {
        AgentOrchestrator orchestrator = orchestrator(
                request -> new PlanningResult(new Plan(List.of(validQueryNode("logs"))), AiUsage.none()),
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
                    if ("query_loki".equals(name)) {
                        throw new IllegalStateException("tool unavailable");
                    }
                    return ToolExecutionResult.of(validReport());
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
                    if ("query_loki".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    return ToolExecutionResult.of(validReport());
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
                    assertThat(event.nodeId()).isEqualTo("logs");
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
                    if ("query_loki".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.TIMEOUT, "timeout");
                    }
                    return ToolExecutionResult.of(validReport());
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
                    if ("query_loki".equals(name) && factsCalls.incrementAndGet() == 1) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    if ("query_loki".equals(name)) {
                        return ToolExecutionResult.of(List.of("logs"));
                    }
                    return ToolExecutionResult.of(validReport());
                },
                100,
                10,
                1);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.report()).contains("catalog-service degradation");
        assertThat(plannerCalls).hasValue(2);
        assertThat(replanFailureContext.get()).contains("MISSING_INFO", "query_loki");
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
                    if ("query_loki".equals(name)) {
                        throw new ToolExecutionException(HarnessErrorCode.MISSING_INFO, "missing info");
                    }
                    return ToolExecutionResult.of(validReport());
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
                    case "query_loki" -> ToolExecutionResult.of(List.of("logs"));
                    case "find_log_signature" -> ToolExecutionResult.of(List.of("signature"));
                    case "test_hypothesis" -> ToolExecutionResult.of(List.of("hypothesis"));
                    case "build_incident_report" -> ToolExecutionResult.of("plain string");
                    default -> throw new IllegalArgumentException(name);
                },
                100,
                10);

        RunResult result = orchestrator.run(new RunRequest("goal", "session-1"));

        assertThat(result.status()).isEqualTo(RunStatus.FAILED_VERIFICATION);
        assertThat(result.error()).contains("IncidentReport");
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
        IncidentInvestigationTools tools = new IncidentInvestigationTools(new IncidentData());
        ToolCatalog toolCatalog = new ToolCatalog(tools);
        return new AgentOrchestrator(
                planner,
                new DagValidator(toolCatalog),
                new IncidentPolicyValidator(toolCatalog),
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
                new PlanNode("logs", "query_loki", List.of(
                        literal("service", "checkout-service"),
                        literal("query", "error timeout"),
                        literal("from", "14:20"),
                        literal("to", "14:40")
                ), List.of()),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of("logs")),
                new PlanNode("hypothesis", "test_hypothesis", List.of(
                        literal("hypothesis", "catalog-service degradation"),
                        nodeResult("evidence", "signature")
                ), List.of("signature")),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "Checkout 5xx increased after 14:32"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature"))
        ));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static PlanNode validQueryNode(String id, String... deps) {
        return new PlanNode(id, "query_loki", List.of(
                literal("service", "checkout-service"),
                literal("query", "error"),
                literal("from", "14:20"),
                literal("to", "14:40")
        ), List.of(deps));
    }

    private static ArgumentBinding literal(String argumentName, String value) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding nodeResult(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }

    private static IncidentReport validReport() {
        return new IncidentReport(
                "catalog-service degradation caused checkout-service 5xx",
                0.89,
                List.of("14:29 deploy", "14:31 catalog latency", "14:32 checkout 5xx"),
                List.of("catalog latency increased", "checkout logs show timeouts", "traces point to catalog"),
                List.of("checkout deployment regression"),
                "Mitigate catalog-service degradation");
    }
}
