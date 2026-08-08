package dev.harness.agent.orchestration;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.budget.BudgetLimits;
import dev.harness.agent.budget.ModelPricing;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RecoveryAction;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import dev.harness.agent.trace.InMemoryTracer;
import dev.harness.agent.trace.TraceEvent;
import dev.harness.agent.trace.Tracer;
import dev.harness.agent.verification.FinalReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class RunLifecycle {

    private static final Logger log = LoggerFactory.getLogger(RunLifecycle.class);

    private final ToolCatalog toolCatalog;

    private final BudgetLimits budgetLimits;

    private final ModelPricing modelPricing;

    RunLifecycle(ToolCatalog toolCatalog, BudgetLimits budgetLimits, ModelPricing modelPricing) {
        this.toolCatalog = toolCatalog;
        this.budgetLimits = budgetLimits;
        this.modelPricing = modelPricing;
    }

    RunState create(RunRequest request) {
        var runId = UUID.randomUUID().toString();
        var sessionId = request == null ? null : request.sessionId();
        Budget budget = new Budget(budgetLimits, modelPricing);
        Tracer tracer = new InMemoryTracer();
        return new RunState(RunPhase.CREATED, request, new RunContext(runId, sessionId, budget, tracer),
                null, null, 0, null);
    }

    RunState start(RunState state) {
        emit(state, "run.start", null, null, "STARTED", "run started");
        log.info("Run {} started", state.context().runId());
        return state.withPhase(RunPhase.PLANNING);
    }

    RunState finish(
            RunState state,
            RunStatus status,
            String report,
            String error,
            ErrorClass errorClass,
            VerificationVerdict verdict) {
        emit(state, "run.finish", null, null, status.name(), error);
        log.info("Run {} finished with status {}", state.context().runId(), status);

        RunContext context = state.context();
        RunResult result = new RunResult(context.runId(), context.sessionId(), status, report, error, errorClass,
                verdict, state.plan(), context.tracer().events(), context.budget().snapshot());
        return state.finished(result);
    }

    void emit(
            RunState state,
            String kind,
            String role,
            String nodeId,
            String status,
            String message) {
        RunContext context = state.context();
        context.tracer().emit(new TraceEvent(Instant.now(), context.runId(), context.sessionId(), kind, role, nodeId,
                status, null, message, context.budget().snapshot(), Map.of()));
    }

    void emitRecoveryDecision(RunState state, ErrorClass errorClass, RecoveryAction action) {
        RunContext context = state.context();
        context.tracer().emit(new TraceEvent(Instant.now(), context.runId(), context.sessionId(), "recovery.decide",
                null, null, action.name(), null, null, context.budget().snapshot(),
                Map.of("errorClass", errorClass.name())));
    }

    void emitNodeEvent(RunState state, PlanNode node, String kind, Duration latency, Budget budget) {
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

        RunContext context = state.context();
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

    String finalReport(Plan plan) {
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
}
