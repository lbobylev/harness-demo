package dev.harness.agent.validation;

import dev.harness.agent.incident.IncidentData;
import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.tools.IncidentInvestigationTools;
import dev.harness.agent.tools.ToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentPolicyValidatorTests {

    private IncidentPolicyValidator validator;

    @BeforeEach
    void setUp() {
        var tools = new IncidentInvestigationTools(new IncidentData());
        validator = new IncidentPolicyValidator(new ToolCatalog(tools));
    }

    @Test
    void acceptsValidFlexibleIncidentDagWithoutAssembleEvidence() {
        assertThatCode(() -> validator.validate(validPlan()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsFinalReportWithoutHypothesisResult() {
        Plan plan = new Plan(List.of(
                boundedQuery("logs", "query_loki"),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "logs"),
                        nodeResult("evidence", "logs")
                ), List.of("logs"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("hypothesisAssessment");
    }

    @Test
    void rejectsHypothesisTestWithoutNodeResultEvidence() {
        Plan plan = new Plan(List.of(
                boundedQuery("logs", "query_loki"),
                new PlanNode("signature", "find_log_signature", List.of(
                        nodeResult("logs", "logs")
                ), List.of("logs")),
                new PlanNode("hypothesis", "test_hypothesis", List.of(
                        literal("hypothesis", "catalog-service degradation"),
                        literal("evidence", "raw assumption")
                ), List.of()),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "signature")
                ), List.of("hypothesis", "signature"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("hypothesis test must consume NODE_RESULT evidence");
    }

    @Test
    void rejectsUnboundedPrometheusQuery() {
        Plan plan = new Plan(List.of(
                new PlanNode("metric", "query_prometheus", List.of(
                        literal("service", "checkout-service"),
                        literal("metric", "5xx_rate"),
                        literal("from", "14:20")
                ), List.of()),
                new PlanNode("hypothesis", "test_hypothesis", List.of(
                        literal("hypothesis", "catalog-service degradation"),
                        nodeResult("evidence", "metric")
                ), List.of("metric")),
                new PlanNode("report", "build_incident_report", List.of(
                        literal("incident", "checkout 5xx increased"),
                        nodeResult("hypothesisAssessment", "hypothesis"),
                        nodeResult("evidence", "metric")
                ), List.of("hypothesis", "metric"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("bounded literal to");
    }

    @Test
    void rejectsFinalReportWithoutEvidenceNodeResult() {
        Plan plan = new Plan(List.of(
                boundedQuery("logs", "query_loki"),
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
                        literal("evidence", "raw assumption")
                ), List.of("hypothesis"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("consume evidence");
    }

    private static Plan validPlan() {
        return new Plan(List.of(
                boundedQuery("logs", "query_loki"),
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

    private static PlanNode boundedQuery(String id, String tool) {
        return new PlanNode(id, tool, List.of(
                literal("service", "checkout-service"),
                literal("query", "error timeout"),
                literal("from", "14:20"),
                literal("to", "14:40")
        ), List.of());
    }

    private static ArgumentBinding literal(String argumentName, String value) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding nodeResult(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }
}
