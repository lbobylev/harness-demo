package dev.harness.agent.verification;

import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.plan.NodeStatus;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.GameRecommendationData;
import dev.harness.agent.tools.GameRecommendationTools;
import dev.harness.agent.tools.ToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReportVerifierTests {

    private ReportVerifier verifier;

    @BeforeEach
    void setUp() {
        GameRecommendationTools tools = new GameRecommendationTools(new GameRecommendationData(), null, new AiUsageExtractor());
        verifier = new ReportVerifier(new ToolCatalog(tools));
    }

    @Test
    void passesCompletedPlanWithFinalReport() {
        PlanNode facts = done(node("facts", "get_genre_facts"), List.of("facts"));
        PlanNode summary = done(node("summary", "summarizer_node", "facts"), new TestReport("Useful final report"));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(facts, summary)));

        assertThat(verdict.passed()).isTrue();
    }

    @Test
    void rejectsMissingFinalSynthesisNode() {
        PlanNode facts = done(node("facts", "get_genre_facts"), List.of("facts"));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(facts)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("FINAL_SYNTHESIS");
    }

    @Test
    void rejectsFinalNodeThatDidNotFinish() {
        PlanNode summary = node("summary", "summarizer_node");

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("must be DONE");
    }

    @Test
    void rejectsFailedOrSkippedNodes() {
        PlanNode failed = node("facts", "get_genre_facts");
        failed.setStatus(NodeStatus.FAILED);
        PlanNode summary = done(node("summary", "summarizer_node", "facts"), new TestReport("Final report"));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(failed, summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("failed or skipped");
    }

    @Test
    void rejectsFinalDependencyThatDidNotFinish() {
        PlanNode facts = node("facts", "get_genre_facts");
        PlanNode summary = done(node("summary", "summarizer_node", "facts"), new TestReport("Final report"));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(facts, summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("dependencies must be DONE");
    }

    @Test
    void rejectsFinalResultWithoutFinalReportContract() {
        PlanNode summary = done(node("summary", "summarizer_node"), "plain string");

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("FinalReport");
    }

    @Test
    void rejectsBlankFinalReport() {
        PlanNode summary = done(node("summary", "summarizer_node"), new TestReport("  "));

        VerificationVerdict verdict = verifier.verify(new Plan(List.of(summary)));

        assertThat(verdict.passed()).isFalse();
        assertThat(verdict.reason()).contains("must not be blank");
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static PlanNode done(PlanNode node, Object result) {
        node.setStatus(NodeStatus.DONE);
        node.setResult(result);
        return node;
    }

    private record TestReport(String reportText) implements FinalReport {
    }
}
