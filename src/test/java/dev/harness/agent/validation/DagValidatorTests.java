package dev.harness.agent.validation;

import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.incident.IncidentData;
import dev.harness.agent.tools.IncidentInvestigationTools;
import dev.harness.agent.tools.ToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DagValidatorTests {

    private DagValidator validator;

    @BeforeEach
    void setUp() {
        IncidentInvestigationTools tools = new IncidentInvestigationTools(new IncidentData());
        validator = new DagValidator(new ToolCatalog(tools));
    }

    @Test
    void rejectsNullNode() {
        Plan plan = new Plan(Collections.singletonList(null));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("plan contains null node");
    }

    @Test
    void rejectsNullDependencyId() {
        Plan plan = new Plan(List.of(
                new PlanNode("logs", "query_loki", Arrays.asList((String) null)),
                node("report", "build_incident_report", "logs")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("dependency id must not be blank");
    }

    @Test
    void acceptsValidFanOutFanInPlan() {
        Plan plan = validPlan();

        assertThatCode(() -> validator.validate(plan)).doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateNodeIds() {
        Plan plan = new Plan(List.of(
                node("logs", "query_loki"),
                node("logs", "query_prometheus"),
                node("report", "build_incident_report", "logs")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("duplicate node id");
    }

    @Test
    void rejectsMissingDependencyReference() {
        Plan plan = new Plan(List.of(
                node("report", "build_incident_report", "missing")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("missing node");
    }

    @Test
    void rejectsUnknownTool() {
        Plan plan = new Plan(List.of(
                node("bad", "delete_database"),
                node("report", "build_incident_report", "bad")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void rejectsCycles() {
        Plan plan = new Plan(List.of(
                validQueryNode("logs", "report"),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of("logs")),
                new PlanNode("hypothesis", "test_hypothesis", List.of(
                        literal("hypothesis", "catalog-service degradation"),
                        nodeResult("evidence", "signature")
                ), List.of("signature")),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("cycle detected");
    }

    @Test
    void rejectsMissingFinalSummaryNode() {
        Plan plan = new Plan(List.of(
                validQueryNode("logs"),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of("logs"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("exactly one FINAL_SYNTHESIS");
    }

    @Test
    void rejectsMultipleFinalSummaryNodes() {
        Plan plan = new Plan(List.of(
                validQueryNode("logs"),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of("logs")),
                new PlanNode("hypothesis", "test_hypothesis", List.of(
                        literal("hypothesis", "catalog-service degradation"),
                        nodeResult("evidence", "signature")
                ), List.of("signature")),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature")),
                new PlanNode("report_2", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("exactly one FINAL_SYNTHESIS");
    }

    @Test
    void rejectsNodeResultArgumentNotListedAsDependency() {
        Plan plan = new Plan(List.of(
                new PlanNode("logs", "query_loki", List.of(
                        literal("service", "checkout-service"),
                        literal("query", "error"),
                        literal("from", "14:20"),
                        literal("to", "14:40")
                ), List.of()),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of()),
                node("report", "build_incident_report", "signature")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("argument source node must be listed in deps");
    }

    @Test
    void rejectsBlankLiteralArgumentValue() {
        Plan plan = new Plan(List.of(
                new PlanNode("logs", "query_loki", List.of(
                        literal("service", " ")
                ), List.of()),
                node("report", "build_incident_report", "logs")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("literal argument value must not be blank");
    }

    @Test
    void rejectsUnknownToolArgument() {
        Plan plan = new Plan(List.of(
                new PlanNode("report", "build_incident_report", List.of(
                        literal("unknown", "value")
                ), List.of())));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("unknown argument");
    }

    @Test
    void rejectsMissingRequiredToolArgument() {
        Plan plan = new Plan(List.of(
                node("hypothesis", "test_hypothesis"),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis")
                ), List.of("hypothesis"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("missing required arguments");
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
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature"))));
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
}
