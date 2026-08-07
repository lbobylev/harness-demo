package dev.harness.agent.verification;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.run.VerificationVerdict;
import dev.harness.agent.tools.ToolCatalog;
import dev.harness.agent.tools.ToolRole;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReportVerifier {

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
        if (!(finalNode.getResult() instanceof FinalReport report)) {
            return VerificationVerdict.failed("final result must implement FinalReport");
        }
        if (report.reportText() == null || report.reportText().isBlank()) {
            return VerificationVerdict.failed("final report must not be blank");
        }

        return VerificationVerdict.pass();
    }
}
