package dev.harness.agent.orchestration;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.execution.DagExecutionResult;
import dev.harness.agent.execution.DagExecutor;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.planning.Planner;
import dev.harness.agent.planning.PlanningRequest;
import dev.harness.agent.planning.PlanningResult;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.ErrorClassifier;
import dev.harness.agent.run.RecoveryAction;
import dev.harness.agent.run.RecoveryPolicy;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import dev.harness.agent.trace.InMemoryTracer;
import dev.harness.agent.trace.TraceEvent;
import dev.harness.agent.trace.Tracer;
import dev.harness.agent.validation.PlanValidationException;
import dev.harness.agent.validation.DagValidator;
import dev.harness.agent.verification.FinalReport;
import dev.harness.agent.verification.ReportVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class AgentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AgentOrchestrator.class);

    private final Planner planner;

    private final DagValidator validator;

    private final DagExecutor executor;

    private final ReportVerifier verifier;

    private final ToolCatalog toolCatalog;

    private final BudgetLimits budgetLimits;

    private final ModelPricing modelPricing;

    private final int maxReplans;

    private record RunContext(String runId, String sessionId, Budget budget, Tracer tracer) {
    }

    public AgentOrchestrator(
            Planner planner,
            DagValidator validator,
            DagExecutor executor,
            ReportVerifier verifier,
            ToolCatalog toolCatalog,
            @Value("${harness.budget.max-tokens:20000}") long maxTokens,
            @Value("${harness.budget.max-tool-calls:50}") long maxToolCalls,
            @Value("${harness.budget.max-wall-clock:60s}") Duration maxWallClock,
            @Value("${harness.budget.max-estimated-cost-usd:0.25}") BigDecimal maxEstimatedCostUsd,
            @Value("${harness.pricing.model:gpt-4.1-mini}") String model,
            @Value("${harness.pricing.input-token-usd:0.0000004}") BigDecimal inputTokenUsd,
            @Value("${harness.pricing.output-token-usd:0.0000016}") BigDecimal outputTokenUsd,
            @Value("${harness.replanning.max-replans:1}") int maxReplans) {
        this.planner = planner;
        this.validator = validator;
        this.executor = executor;
        this.verifier = verifier;
        this.toolCatalog = toolCatalog;
        this.budgetLimits = new BudgetLimits(maxTokens, maxToolCalls, maxWallClock, maxEstimatedCostUsd);
        this.modelPricing = new ModelPricing(model, inputTokenUsd, outputTokenUsd);
        this.maxReplans = Math.max(0, maxReplans);
    }

    public RunResult run(RunRequest request) {
        var runId = UUID.randomUUID().toString();
        var sessionId = request == null ? null : request.sessionId();
        var budget = new Budget(budgetLimits, modelPricing);
        var tracer = new InMemoryTracer();
        var context = new RunContext(runId, sessionId, budget, tracer);
        Plan plan = null;
        String failureContext = null;

        emit(context, "run.start", null, null, "STARTED", "run started");
        log.info("Run {} started", runId);

        for (int attempt = 0; attempt <= maxReplans; attempt++) {
            PlanningResult planningResult;
            try {
                log.info("Run {} planning started for attempt {}", runId, attempt);
                planningResult = planner.plan(new PlanningRequest(request.goal(), toolCatalogForPrompt(), failureContext));
            } catch (Exception exception) {
                log.warn("Run {} planning failed: {}", runId, exception.getMessage());
                emit(context, "planning.finish", null, null, "FAILED", exception.getMessage());
                return finish(context, RunStatus.FAILED_PLANNING, null,
                        exception.getMessage(), ErrorClass.FATAL, null, plan);
            }

            plan = planningResult.plan();
            budget.chargeUsage(planningResult.usage());
            emit(context, "planning.finish", null, null, "DONE", "planning finished");
            log.info("Run {} planning finished for attempt {}", runId, attempt);
            if (budget.exhausted()) {
                return finish(context, RunStatus.BUDGET_EXHAUSTED, null,
                        "budget exhausted after planning", ErrorClass.FATAL, null, plan);
            }

            try {
                log.info("Run {} validation started", runId);
                validator.validate(plan);
                emit(context, "validation.finish", null, null, "DONE", "validation finished");
                log.info("Run {} validation finished", runId);
            } catch (PlanValidationException exception) {
                log.warn("Run {} validation failed: {}", runId, exception.getMessage());
                emit(context, "validation.finish", null, null, "FAILED", exception.getMessage());
                var action = RecoveryPolicy.decide(ErrorClass.VALIDATION);
                emitRecoveryDecision(context, ErrorClass.VALIDATION, action);
                if (canReplan(action, attempt, budget)) {
                    failureContext = "validation failed: " + exception.getMessage();
                    emit(context, "replanning.start", null, null, "STARTED", failureContext);
                    continue;
                }
                return finish(context, RunStatus.FAILED_VALIDATION, null,
                        exception.getMessage(), ErrorClass.VALIDATION, null, plan);
            }

            DagExecutionResult executionResult;
            try {
                log.info("Run {} execution started", runId);
                executionResult = executor.execute(plan, budget,
                        (node, kind, latency, currentBudget) -> emitNodeEvent(
                                context, node, kind, latency, currentBudget));
            } catch (Exception exception) {
                log.warn("Run {} execution failed: {}", runId, exception.getMessage());
                emit(context, "execution.finish", null, null, "FAILED", exception.getMessage());
                return finishWithRecoveryDecision(context, RunStatus.FAILED_EXECUTION, null,
                        exception.getMessage(), ErrorClass.FATAL, null, plan);
            }
            emit(context, "execution.finish", null, null,
                    executionResult.successful() ? "DONE" : "FAILED", "execution finished");
            log.info("Run {} execution finished with success={}", runId, executionResult.successful());
            if (budget.exhausted()) {
                return finish(context, RunStatus.BUDGET_EXHAUSTED, null,
                        "budget exhausted after execution", ErrorClass.FATAL, null, plan);
            }
            if (!executionResult.successful()) {
                var errorClass = classifyExecutionFailure(plan);
                var action = RecoveryPolicy.decide(errorClass);
                emitRecoveryDecision(context, errorClass, action);
                if (canReplan(action, attempt, budget)) {
                    failureContext = buildExecutionFailureContext(plan, errorClass);
                    emit(context, "replanning.start", null, null, "STARTED", failureContext);
                    continue;
                }
                return finish(context, RunStatus.FAILED_EXECUTION, null,
                        "execution failed", errorClass, null, plan);
            }

            VerificationVerdict verdict;
            try {
                log.info("Run {} verification started", runId);
                verdict = verifier.verify(plan);
            } catch (Exception exception) {
                log.warn("Run {} verification failed: {}", runId, exception.getMessage());
                emit(context, "verification.finish", null, null, "FAILED", exception.getMessage());
                return finish(context, RunStatus.FAILED_VERIFICATION, null,
                        exception.getMessage(), ErrorClass.FATAL, null, plan);
            }
            emit(context, "verification.finish", null, null,
                    verdict.passed() ? "DONE" : "FAILED", verdict.reason());
            log.info("Run {} verification finished with passed={}", runId, verdict.passed());
            if (!verdict.passed()) {
                return finish(context, RunStatus.FAILED_VERIFICATION, null,
                        verdict.reason(), ErrorClass.VALIDATION, verdict, plan);
            }

            return finish(context, RunStatus.SUCCEEDED, finalReport(plan), null,
                    null, verdict, plan);
        }

        return finish(context, RunStatus.FAILED_EXECUTION, null,
                "replanning failed", ErrorClass.FATAL, null, plan);
    }

    private String toolCatalogForPrompt() {
        return toolCatalog.definitions().toString();
    }

    private ErrorClass classifyExecutionFailure(Plan plan) {
        if (plan == null) {
            return ErrorClass.FATAL;
        }

        return failedNodes(plan).stream()
                .map(PlanNode::getErrorCode)
                .map(ErrorClassifier::classify)
                .reduce(ErrorClass.VALIDATION, AgentOrchestrator::moreSevere);
    }

    private List<PlanNode> failedNodes(Plan plan) {
        return plan.nodes().stream()
                .filter(node -> node != null && node.isFailed())
                .toList();
    }

    private boolean canReplan(RecoveryAction action, int attempt, Budget budget) {
        return action == RecoveryAction.REPLAN && attempt < maxReplans && budget.hasRoom();
    }

    private String buildExecutionFailureContext(Plan plan, ErrorClass errorClass) {
        if (plan == null) {
            return "execution failed: " + errorClass;
        }

        var failedNodes = failedNodes(plan).stream()
                .map(node -> "node=%s tool=%s errorCode=%s error=%s"
                        .formatted(node.getId(), node.getTool(), node.getErrorCode(), node.getError()))
                .collect(Collectors.joining("\n"));
        if (failedNodes.isBlank()) {
            return "execution failed: " + errorClass;
        }
        return "execution failed with %s:\n%s".formatted(errorClass, failedNodes);
    }

    private static ErrorClass moreSevere(ErrorClass left, ErrorClass right) {
        return severity(right) > severity(left) ? right : left;
    }

    private static int severity(ErrorClass errorClass) {
        return switch (errorClass) {
            case VALIDATION -> 0;
            case MISSING_INFO -> 1;
            case TRANSIENT -> 2;
            case FATAL -> 3;
        };
    }

    private RunResult finish(
            RunContext context,
            RunStatus status,
            String report,
            String error,
            ErrorClass errorClass,
            VerificationVerdict verdict,
            Plan plan) {
        emit(context, "run.finish", null, null, status.name(), error);
        log.info("Run {} finished with status {}", context.runId(), status);
        return new RunResult(context.runId(), context.sessionId(), status, report, error, errorClass, verdict, plan,
                context.tracer().events(), context.budget().snapshot());
    }

    private RunResult finishWithRecoveryDecision(
            RunContext context,
            RunStatus status,
            String report,
            String error,
            ErrorClass errorClass,
            VerificationVerdict verdict,
            Plan plan) {
        var action = RecoveryPolicy.decide(errorClass);
        emitRecoveryDecision(context, errorClass, action);
        return finish(context, status, report, error, errorClass, verdict, plan);
    }

    private String finalReport(Plan plan) {
        if (plan == null) {
            return null;
        }
        return plan.nodes().stream()
                .filter(node -> node != null && toolCatalog.roleOf(node.getTool()) == ToolRole.FINAL_SYNTHESIS)
                .map(PlanNode::getResult)
                .filter(FinalReport.class::isInstance)
                .map(FinalReport.class::cast)
                .map(FinalReport::reportText)
                .findFirst()
                .orElse(null);
    }

    private void emit(
            RunContext context,
            String kind,
            String role,
            String nodeId,
            String status,
            String message) {
        context.tracer().emit(new TraceEvent(Instant.now(), context.runId(), context.sessionId(), kind, role, nodeId,
                status, null, message, context.budget().snapshot(), Map.of()));
    }

    private void emitRecoveryDecision(
            RunContext context,
            ErrorClass errorClass,
            RecoveryAction action) {
        context.tracer().emit(new TraceEvent(Instant.now(), context.runId(), context.sessionId(), "recovery.decide",
                null, null, action.name(), null, null, context.budget().snapshot(),
                Map.of("errorClass", errorClass.name())));
    }

    private void emitNodeEvent(
            RunContext context,
            PlanNode node,
            String kind,
            Duration latency,
            Budget budget) {
        var data = new HashMap<String, Object>();
        data.put("tool", node.getTool());
        if (node.getUsage() != null) {
            data.put("inputTokens", node.getUsage().inputTokens());
            data.put("outputTokens", node.getUsage().outputTokens());
            data.put("totalTokens", node.getUsage().totalTokens());
        }
        if (node.getErrorCode() != null) {
            data.put("errorCode", node.getErrorCode().name());
        }

        context.tracer().emit(new TraceEvent(
                Instant.now(),
                context.runId(),
                context.sessionId(),
                kind,
                toolCatalog.roleOf(node.getTool()).name(),
                node.getId(),
                node.getStatus().name(),
                latency == null ? null : latency.toMillis(),
                node.getError(),
                budget.snapshot(),
                data));
    }
}
