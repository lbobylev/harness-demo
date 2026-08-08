package dev.harness.agent.planning;

import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.plan.Plan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class SpringAiPlanner implements Planner {

    private static final String SYSTEM_PROMPT = """
            You are a planner for a Spring AI incident investigation harness.

            Compile the user's incident goal into a dependency graph of tool calls.
            Return only the structured Plan object requested by the API.

            Investigation rules:
            - Do not invent tool names.
            - Use only tools listed in the tool catalog.
            - Plan an evidence-gathering investigation, not a direct final answer.
            - Prefer a simple fan-out/fan-in DAG: gather independent evidence first, then analyze it, test a hypothesis, and build the report.
            - Start with metrics to confirm the symptom and time window.
            - Check deployments and config changes near the incident window for temporal correlation.
            - Query logs in the incident window to find concrete failure signatures.
            - Do not query Tempo by default.
            - Query traces only when earlier evidence or failure context suggests request-path, timeout, downstream dependency, traceId, or span-level details are needed.
            - Trace evidence is optional. If assemble_evidence is used and no trace evidence is available, pass an empty literal {} for traces.
            - Use compare_periods for metric evidence when comparing baseline and incident windows.
            - Use find_log_signature for Loki log results before using logs as failure-pattern evidence.
            - Use correlate or another analysis result to provide structured evidence to test_hypothesis.
            - Respect tool result types in the catalog. Pass NODE_RESULT only where the receiving argument expects that result type.
            - Analysis type flow: compare_periods returns PeriodComparison; find_log_signature returns LogSignature; assemble_evidence returns EvidenceBundle; correlate requires EvidenceBundle and returns CorrelationResult; test_hypothesis requires CorrelationResult and returns HypothesisAssessment; build_incident_report requires HypothesisAssessment and CorrelationResult.
            - Do not pass PeriodComparison directly to correlate. Do not pass EvidenceBundle directly to test_hypothesis.
            - Use build_incident_report exactly once as the FINAL_SYNTHESIS tool.
            - Do not build the final report before testing a hypothesis.
            - The final report must consume hypothesisAssessment from a HYPOTHESIS_TEST node result and evidence from an evidence or analysis node result.
            - If previous failure context mentions missing evidence, plan tool calls that gather that missing evidence.
            - If previous failure context says to test another hypothesis, make that hypothesis the literal hypothesis argument in test_hypothesis.

            Plan format rules:
            - Nodes may run in parallel when their deps are empty or already satisfied.
            - deps are control flow only: they only declare execution order.
            - arguments are data flow only: they pass literals or node results to tool parameters.
            - Tool arguments belong in each node's arguments list.
            - Every required tool argument from the input schema must appear explicitly in the node's arguments list.
            - Each argument has argumentName and value.
            - value.type must be LITERAL when the value is known from the user goal. Put the text in value.literalValue and leave value.sourceNodeId empty.
            - value.type must be NODE_RESULT when the value comes from another node. Put that node id in value.sourceNodeId, leave value.literalValue empty, and include the source node id in deps.
            - Observability query tools must use bounded literal from/to time windows.
            - Do not rely on deps alone to pass data between nodes.
            - Do not use a generic action protocol such as {type, tool, args}; return the Plan domain model.
            """;

    private final ChatClient chatClient;

    private final AiUsageExtractor usageExtractor;

    public SpringAiPlanner(ChatClient chatClient, AiUsageExtractor usageExtractor) {
        this.chatClient = chatClient;
        this.usageExtractor = usageExtractor;
    }

    @Override
    public PlanningResult plan(PlanningRequest request) {
        Assert.notNull(request, "Planning request must not be null");
        Assert.hasText(request.goal(), "Planning goal must not be blank");
        Assert.hasText(request.toolCatalog(), "Tool catalog must not be blank");

        ResponseEntity<ChatResponse, Plan> response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(request))
                .call()
                .responseEntity(Plan.class, spec -> spec.useProviderStructuredOutput().validateSchema());

        Plan plan = response.entity();
        if (plan == null) {
            throw new IllegalStateException("Planner returned an empty plan");
        }

        AiUsage usage = usageExtractor.extract(response.response());
        return new PlanningResult(plan, usage);
    }

    private static String userPrompt(PlanningRequest request) {
        String prompt = """
                GOAL:
                %s

                AVAILABLE TOOLS:
                %s
                """.formatted(request.goal(), request.toolCatalog());
        if (request.failureContext() == null || request.failureContext().isBlank()) {
            return prompt;
        }

        return prompt + """

                PREVIOUS FAILURE:
                %s

                Revise the plan to avoid repeating the failed action.
                """.formatted(request.failureContext());
    }
}
