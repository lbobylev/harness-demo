package dev.harness.agent.validation;

import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.Plan;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.tools.GameRecommendationData;
import dev.harness.agent.tools.GameRecommendationTools;
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
        GameRecommendationTools tools = new GameRecommendationTools(new GameRecommendationData(), null, new AiUsageExtractor());
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
                new PlanNode("facts", "get_genre_facts", Arrays.asList((String) null)),
                node("summary", "summarizer_node", "facts")));

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
                node("facts", "get_genre_facts"),
                node("facts", "get_genre_reviews"),
                node("summary", "summarizer_node", "facts")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("duplicate node id");
    }

    @Test
    void rejectsMissingDependencyReference() {
        Plan plan = new Plan(List.of(
                node("summary", "summarizer_node", "missing")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("missing node");
    }

    @Test
    void rejectsUnknownTool() {
        Plan plan = new Plan(List.of(
                node("bad", "delete_database"),
                node("summary", "summarizer_node", "bad")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void rejectsCycles() {
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts", "summary"),
                node("summary", "summarizer_node", "facts")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("cycle detected");
    }

    @Test
    void rejectsMissingFinalSummaryNode() {
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("reviews", "get_genre_reviews")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("exactly one FINAL_SYNTHESIS");
    }

    @Test
    void rejectsMultipleFinalSummaryNodes() {
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                node("summary", "summarizer_node", "facts"),
                node("summary_2", "summarizer_node", "facts")));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("exactly one FINAL_SYNTHESIS");
    }

    @Test
    void rejectsNodeResultArgumentNotListedAsDependency() {
        Plan plan = new Plan(List.of(
                node("facts", "get_genre_facts"),
                new PlanNode("summary", "summarizer_node", List.of(
                        nodeResult("genreFacts", "facts")
                ), List.of())));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("argument source node must be listed in deps");
    }

    @Test
    void rejectsBlankLiteralArgumentValue() {
        Plan plan = new Plan(List.of(
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("preferences", " ")
                ), List.of())));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("literal argument value must not be blank");
    }

    @Test
    void rejectsUnknownToolArgument() {
        Plan plan = new Plan(List.of(
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("unknown", "value")
                ), List.of())));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("unknown argument");
    }

    @Test
    void rejectsMissingRequiredToolArgument() {
        Plan plan = new Plan(List.of(
                node("genre_facts", "get_genre_facts"),
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("preferences", "cozy exploration"),
                        nodeResult("genreFacts", "genre_facts")
                ), List.of("genre_facts"))));

        assertThatThrownBy(() -> validator.validate(plan))
                .isInstanceOf(PlanValidationException.class)
                .hasMessageContaining("missing required arguments");
    }

    private static Plan validPlan() {
        return new Plan(List.of(
                node("genre_facts", "get_genre_facts"),
                node("genre_reviews", "get_genre_reviews"),
                node("games", "get_games"),
                node("prices", "get_prices"),
                new PlanNode("summary", "summarizer_node", List.of(
                        literal("preferences", "cozy exploration"),
                        nodeResult("genreFacts", "genre_facts"),
                        nodeResult("genreReviews", "genre_reviews"),
                        nodeResult("games", "games"),
                        nodeResult("prices", "prices")
                ), List.of("genre_facts", "genre_reviews", "games", "prices"))));
    }

    private static PlanNode node(String id, String tool, String... deps) {
        return new PlanNode(id, tool, List.of(deps));
    }

    private static ArgumentBinding literal(String argumentName, String value) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.LITERAL, value, null));
    }

    private static ArgumentBinding nodeResult(String argumentName, String sourceNodeId) {
        return new ArgumentBinding(argumentName, new ArgumentValue(ArgumentValueType.NODE_RESULT, null, sourceNodeId));
    }
}
