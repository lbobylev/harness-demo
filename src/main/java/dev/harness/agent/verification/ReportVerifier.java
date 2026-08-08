package dev.harness.agent.verification;

import dev.harness.agent.incident.IncidentReport;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportVerifier {

    private static final double MIN_CONFIDENCE = 0.75;

    private final ToolCatalog toolCatalog;

    public ReportVerifier(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    public VerificationVerdict verify(Plan plan) {
        if (plan == null) {
            return VerificationVerdict.failed("plan must not be null");
        }

        List<PlanNode> finalNodes = plan.nodes().stream()
                .filter(node -> node != null && toolCatalog.roleOf(node.getTool()) == ToolRole.FINAL_SYNTHESIS)
                .toList();
        if (finalNodes.size() != 1) {
            return VerificationVerdict.failed("plan must contain exactly one FINAL_SYNTHESIS node");
        }

        var finalNode = finalNodes.getFirst();
        if (!finalNode.isDone()) {
            return VerificationVerdict.failed("final synthesis node must be DONE");
        }
        if (plan.nodes().stream().anyMatch(node -> node != null && (node.isFailed() || node.isSkipped()))) {
            return VerificationVerdict.failed("plan contains failed or skipped nodes");
        }
        if (plan.getDepNodes(finalNode).stream().anyMatch(dep -> dep == null || !dep.isDone())) {
            return VerificationVerdict.failed("final synthesis dependencies must be DONE");
        }
        if (!(finalNode.getResult() instanceof IncidentReport report)) {
            return VerificationVerdict.failed("final result must be an IncidentReport");
        }
        if (report.rootCause() == null || report.rootCause().isBlank()) {
            return VerificationVerdict.failed("incident report rootCause must not be blank");
        }
        if (report.confidence() < MIN_CONFIDENCE) {
            return VerificationVerdict.failed("incident report confidence must be at least %.2f".formatted(MIN_CONFIDENCE));
        }
        if (report.timeline().size() < 3) {
            return VerificationVerdict.failed("incident report timeline must contain at least 3 entries");
        }
        if (report.evidence().size() < 3) {
            return VerificationVerdict.failed("incident report evidence must contain at least 3 items");
        }
        if (report.rejectedHypotheses().isEmpty()) {
            return VerificationVerdict.failed("incident report must include rejected hypotheses");
        }
        if (report.recommendedAction() == null || report.recommendedAction().isBlank()) {
            return VerificationVerdict.failed("incident report recommendedAction must not be blank");
        }
        if (report.reportText() == null || report.reportText().isBlank()) {
            return VerificationVerdict.failed("final report must not be blank");
        }

        return VerificationVerdict.pass();
    }
}
