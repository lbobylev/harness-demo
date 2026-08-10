package dev.harness.lg4j;

import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static dev.harness.agent.tools.IncidentInvestigationTools.ASSEMBLE_EVIDENCE;
import static dev.harness.agent.tools.IncidentInvestigationTools.BUILD_INCIDENT_REPORT;
import static dev.harness.agent.tools.IncidentInvestigationTools.CORRELATE;
import static dev.harness.agent.tools.IncidentInvestigationTools.TEST_HYPOTHESIS;

final class Lg4jPlanShapeAnalyzer {

    Lg4jPlanShape analyze(Plan plan) {
        var assemble = singleNodeByTool(plan, ASSEMBLE_EVIDENCE);
        var correlate = singleNodeByTool(plan, CORRELATE);
        var test = singleNodeByTool(plan, TEST_HYPOTHESIS);
        var report = singleNodeByTool(plan, BUILD_INCIDENT_REPORT);

        requireDependency(correlate, assemble);
        requireDependency(test, correlate);
        requireDependency(report, test);

        var seenBranchNodes = new HashSet<String>();
        var branches = new ArrayList<List<String>>();
        for (var depId : assemble.getDeps()) {
            var branch = branchToRoot(plan, depId);
            for (var nodeId : branch) {
                if (!seenBranchNodes.add(nodeId)) {
                    throw unsupported("branch node '%s' is shared by multiple branches".formatted(nodeId));
                }
            }
            branches.add(branch);
        }
        if (branches.isEmpty()) {
            throw unsupported("assemble_evidence must depend on at least one evidence branch");
        }

        return new Lg4jPlanShape(branches, List.of(
                assemble.getId(), correlate.getId(), test.getId(), report.getId()));
    }

    private List<String> branchToRoot(Plan plan, String nodeId) {
        var result = new ArrayList<String>();
        var seen = new HashSet<String>();
        var current = plan.getNodeById(nodeId);

        while (current != null && seen.add(current.getId())) {
            result.add(current.getId());
            if (current.getDeps().isEmpty()) {
                Collections.reverse(result);
                return result;
            }
            if (current.getDeps().size() != 1) {
                throw unsupported("branch node '%s' has multiple dependencies".formatted(current.getId()));
            }
            current = plan.getNodeById(current.getDeps().getFirst());
        }

        throw unsupported("unknown branch node '%s'".formatted(nodeId));
    }

    private PlanNode singleNodeByTool(Plan plan, String tool) {
        var matches = plan.nodes().stream()
                .filter(node -> node != null && tool.equals(node.getTool()))
                .toList();
        if (matches.size() != 1) {
            throw unsupported("expected exactly one " + tool);
        }
        return matches.getFirst();
    }

    private void requireDependency(PlanNode node, PlanNode dependency) {
        if (!node.getDeps().contains(dependency.getId())) {
            throw unsupported("%s must depend on %s".formatted(node.getTool(), dependency.getTool()));
        }
    }

    private IllegalArgumentException unsupported(String message) {
        return new IllegalArgumentException("unsupported lg4j plan shape: " + message);
    }
}
