package dev.harness.lg4j.state;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.lg4j.incident.Lg4jIncidentAnalysis;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

public final class Lg4jRunState extends AgentState {

    public static final String GOAL = "goal";
    public static final String SESSION_ID = "sessionId";
    public static final String RUN_ID = "runId";
    public static final String PLAN = "plan";
    public static final String BUDGET = "budget";
    public static final String FAILURE_CONTEXT = "failureContext";
    public static final String STATUS = "status";
    public static final String INCIDENT_ANALYSIS = "incidentAnalysis";
    public static final String INCIDENT_REPORT = "incidentReport";
    public static final String REPORT = "report";
    public static final String ERROR = "error";
    public static final String ERROR_CLASS = "errorClass";
    public static final String VERDICT = "verdict";
    public static final String REPLAN_COUNT = "replanCount";
    public static final String NEEDS_REPLAN = "needsReplan";

    public static final Map<String, Channel<?>> SCHEMA = Map.of();

    public Lg4jRunState(Map<String, Object> initData) {
        super(initData);
    }

    public Optional<String> runId() {
        return value(RUN_ID);
    }

    public Optional<String> sessionId() {
        return value(SESSION_ID);
    }

    public Optional<String> goal() {
        return value(GOAL);
    }

    public Optional<Plan> plan() {
        return value(PLAN);
    }

    public Optional<BudgetSnapshot> budget() {
        return value(BUDGET);
    }

    public Optional<String> failureContext() {
        return value(FAILURE_CONTEXT);
    }

    public Optional<RunStatus> status() {
        return value(STATUS);
    }

    public Optional<String> report() {
        return value(REPORT);
    }

    public Optional<Lg4jIncidentAnalysis> incidentAnalysis() {
        return value(INCIDENT_ANALYSIS);
    }

    public Optional<IncidentReport> incidentReport() {
        return value(INCIDENT_REPORT);
    }

    public Optional<String> error() {
        return value(ERROR);
    }

    public Optional<ErrorClass> errorClass() {
        return value(ERROR_CLASS);
    }

    public Optional<VerificationVerdict> verdict() {
        return value(VERDICT);
    }

    public int replanCount() {
        return value(REPLAN_COUNT)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(0);
    }

    public boolean needsReplan() {
        return value(NEEDS_REPLAN)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    public boolean terminal() {
        return status().isPresent();
    }

}
