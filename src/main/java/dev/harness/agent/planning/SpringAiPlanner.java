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
            You are a planner for a Spring AI agentic harness.

            Produce a dependency graph of tool calls that satisfies the user's goal.
            Return only the structured Plan object requested by the API.

            Rules:
            - Do not invent tool names.
            - Use only tools listed in the tool catalog.
            - Nodes may run in parallel when their deps are empty or already satisfied.
             - Prefer a simple fan-out/fan-in DAG: collect data independently, then synthesize.
             - Include exactly one tool marked FINAL_SYNTHESIS for final recommendation goals.
             - The FINAL_SYNTHESIS node must depend on all relevant upstream data-collection nodes.
             - Tool arguments belong in each node's arguments list.
             - Every required tool argument from the input schema must appear explicitly in the node's arguments list.
             - Do not rely on deps alone to pass data between nodes; deps only declare execution order.
             - Each argument has argumentName and value.
             - value.type must be LITERAL when the value is known from the user goal. Put the text in value.literalValue and leave value.sourceNodeId empty.
             - value.type must be NODE_RESULT when the value comes from another node. Put that node id in value.sourceNodeId, leave value.literalValue empty, and include the source node id in deps.
             - Use empty arguments for tools that take no inputs.
             - For FINAL_SYNTHESIS tools, bind literal user preferences from the user's goal and bind required data arguments from corresponding upstream node results.
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
