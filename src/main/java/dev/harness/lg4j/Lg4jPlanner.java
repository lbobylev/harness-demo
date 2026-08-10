package dev.harness.lg4j;

import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.plan.Plan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
class Lg4jPlanner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Lg4jPlanner.class);

    private static final String SYSTEM_PROMPT = """
            You are a planner for an incident investigation harness.

            Compile the user's incident goal into an evidence DAG.
            Return only the structured Plan object requested by the API.

            Rules:
            - Use only tools listed in the tool catalog.
            - Nodes are tool calls.
            - deps are execution dependencies.
            - arguments are explicit data bindings.
            - Use LITERAL for values known from the user goal.
            - Use NODE_RESULT for values produced by another node.
            - Every NODE_RESULT sourceNodeId must also appear in deps.
            - Every required tool argument from the catalog must appear explicitly.
            - Return only evidence collection and local evidence analysis before fan-in.
            - Read terminal=true or terminal=false from AVAILABLE TOOLS.
            - The Plan may contain any acyclic dependency graph over AVAILABLE TOOLS.
            - Every terminal node must use a tool marked terminal=true.
            - Tools marked terminal=false may only be intermediate DAG nodes.
            - Do not create tools or runtime synthesis/report nodes that are not listed in AVAILABLE TOOLS.
            - The runtime will fan in all terminal nodes, then deterministically analyze evidence and build the report.
            - Do not return Java code or a generic action protocol.
            """;

    private final ChatClient chatClient;

    private final AiUsageExtractor usageExtractor;

    Lg4jPlanner(ChatClient chatClient, AiUsageExtractor usageExtractor) {
        this.chatClient = chatClient;
        this.usageExtractor = usageExtractor;
    }

    Lg4jPlanningResult plan(String goal, String failureContext) {
        Assert.hasText(goal, "Planning goal must not be blank");

        var response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(userPrompt(goal, failureContext))
                .call()
                .responseEntity(Plan.class, spec -> spec.useProviderStructuredOutput().validateSchema());

        var plan = response.entity();
        if (plan == null) {
            throw new IllegalStateException("Planner returned an empty plan");
        }

        log.info(Lg4jDebugValue.dump(plan));

        return new Lg4jPlanningResult(plan, usageExtractor.extract(response.response()));
    }

    private String userPrompt(String goal, String failureContext) {
        String prompt = """
                GOAL:
                %s

                AVAILABLE TOOLS:
                %s
                """.formatted(goal, Lg4jToolSpecs.promptCatalog());
        if (failureContext == null || failureContext.isBlank()) {
            return prompt;
        }

        return prompt + """

                PREVIOUS FAILURE:
                %s

                Revise the plan to avoid repeating the failed action.
                """.formatted(failureContext);
    }
}
