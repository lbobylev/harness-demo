package dev.harness.lg4j;

import dev.harness.agent.ai.AiUsageExtractor;
import dev.harness.agent.plan.Plan;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
class Lg4jPlanner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Lg4jPlanner.class);

    private static final String TOOLS = """
            query_prometheus(service, metric, from, to) -> PrometheusQueryResult role=EVIDENCE
            query_loki(service, query, from, to) -> LokiQueryResult role=EVIDENCE
            query_tempo(service, query, from, to) -> TempoQueryResult role=EVIDENCE
            get_deployments(service, from, to) -> List<DeploymentEvent> role=EVIDENCE
            get_config_changes(service, from, to) -> List<ConfigChange> role=EVIDENCE
            compare_periods(metricSeries, baselineFrom, baselineTo, incidentFrom, incidentTo) -> PeriodComparison role=ANALYSIS
            find_log_signature(logs) -> LogSignature role=ANALYSIS
            assemble_evidence(metricComparison, logSignature, traces, deployments, configChanges) -> EvidenceBundle role=ANALYSIS
            correlate(evidence) -> CorrelationResult role=ANALYSIS
            test_hypothesis(hypothesis, evidence) -> HypothesisAssessment role=HYPOTHESIS_TEST
            build_incident_report(incident, hypothesisAssessment, evidence) -> IncidentReport role=FINAL_SYNTHESIS
            """;

    private static final String SYSTEM_PROMPT = """
            You are a planner for an incident investigation harness.

            Compile the user's incident goal into a dependency graph of tool calls.
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
            - Use build_incident_report exactly once as the final synthesis tool.
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
                """.formatted(goal, TOOLS);
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
