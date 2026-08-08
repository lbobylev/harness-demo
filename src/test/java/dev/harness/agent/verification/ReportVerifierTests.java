package dev.harness.agent.verification;

import dev.harness.agent.incident.IncidentData;
import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.IncidentInvestigationTools;
import dev.harness.agent.tools.ToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportVerifierTests {

    private ReportVerifier verifier;

    @BeforeEach
    void setUp() {
        IncidentInvestigationTools tools = new IncidentInvestigationTools(new IncidentData());
        verifier = new ReportVerifier(new ToolCatalog(tools));
    }

    @Test
    void passesCompletedPlanWithFinalReport() {
        PlanNode hypothesis = done(node("hypothesis", "test_hypothesis"), List.of("hypothesis"));
        PlanNode summary = done(node("summary", "build_incident_report", "hypothesis"), validReport());

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(hypothesis, summary)));

        assertThat(verdict.passed()).isTrue();
    }

    @Test
    void rejectsMissingFinalSynthesisNode() {
        PlanNode facts = done(node("facts", "query_loki"), List.of("facts"));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(facts)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("FINAL_SYNTHESIS");
    }

    @Test
    void rejectsFinalNodeThatDidNotFinish() {
        PlanNode summary = node("summary", "build_incident_report");

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("must be DONE");
    }

    @Test
    void rejectsFailedOrSkippedNodes() {
        PlanNode failed = node("facts", "query_loki");
        failed.setStatus(NodeStatus.FAILED);
        PlanNode summary = done(node("summary", "build_incident_report", "facts"), validReport());

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(failed, summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("failed or skipped");
    }

    @Test
    void rejectsFinalDependencyThatDidNotFinish() {
        PlanNode facts = node("facts", "test_hypothesis");
        PlanNode summary = done(node("summary", "build_incident_report", "facts"), validReport());

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(facts, summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("dependencies must be DONE");
    }

    @Test
    void rejectsFinalResultWithoutFinalReportContract() {
        PlanNode summary = done(node("summary", "build_incident_report"), "plain string");

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("IncidentReport");
    }

    @Test
    void rejectsWeakIncidentReport() {
        PlanNode summary = done(node("summary", "build_incident_report"), new IncidentReport(
                " ", 0.1, List.of(), List.of(), List.of(), ""));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("rootCause");
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static PlanNode done(PlanNode node, Object result) {
        node.setStatus(NodeStatus.DONE);
        node.setResult(result);
        return node;
    }

    private static IncidentReport validReport() {
        return new IncidentReport(
                "catalog-service degradation caused checkout failures",
                0.89,
                List.of("14:29 deploy", "14:31 catalog latency", "14:32 checkout 5xx"),
                List.of("catalog latency increased", "checkout logs show timeouts", "traces point to catalog"),
                List.of("checkout deployment regression"),
                "Mitigate catalog-service degradation");
    }
}
