package dev.harness.lg4j;

import dev.harness.agent.budget.BudgetSnapshot;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.run.ErrorClass;
import dev.harness.agent.run.RunStatus;
import dev.harness.agent.run.VerificationVerdict;
import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;

import java.util.Map;
import java.util.Optional;

final class Lg4jRunState extends AgentState {

    static final String GOAL = "goal";
    static final String SESSION_ID = "sessionId";
    static final String RUN_ID = "runId";
    static final String PLAN = "plan";
    static final String BUDGET = "budget";
    static final String FAILURE_CONTEXT = "failureContext";
    static final String STATUS = "status";
    static final String INCIDENT_ANALYSIS = "incidentAnalysis";
    static final String INCIDENT_REPORT = "incidentReport";
    static final String REPORT = "report";
    static final String ERROR = "error";
    static final String ERROR_CLASS = "errorClass";
    static final String VERDICT = "verdict";
    static final String REPLAN_COUNT = "replanCount";
    static final String NEEDS_REPLAN = "needsReplan";

    static final Map<String, Channel<?>> SCHEMA = Map.of();

    Lg4jRunState(Map<String, Object> initData) {
        super(initData);
    }

    Optional<String> runId() {
        return value(RUN_ID);
    }

    Optional<String> sessionId() {
        return value(SESSION_ID);
    }

    Optional<String> goal() {
        return value(GOAL);
    }

    Optional<Plan> plan() {
        return value(PLAN);
    }

    Optional<BudgetSnapshot> budget() {
        return value(BUDGET);
    }

    Optional<String> failureContext() {
        return value(FAILURE_CONTEXT);
    }

    Optional<RunStatus> status() {
        return value(STATUS);
    }

    Optional<String> report() {
        return value(REPORT);
    }

    Optional<Lg4jIncidentAnalysis> incidentAnalysis() {
        return value(INCIDENT_ANALYSIS);
    }

    Optional<IncidentReport> incidentReport() {
        return value(INCIDENT_REPORT);
    }

    Optional<String> error() {
        return value(ERROR);
    }

    Optional<ErrorClass> errorClass() {
        return value(ERROR_CLASS);
    }

    Optional<VerificationVerdict> verdict() {
        return value(VERDICT);
    }

    int replanCount() {
        return value(REPLAN_COUNT)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .map(Number::intValue)
                .orElse(0);
    }

    boolean needsReplan() {
        return value(NEEDS_REPLAN)
                .filter(Boolean.class::isInstance)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    boolean terminal() {
        return status().isPresent();
    }

}
