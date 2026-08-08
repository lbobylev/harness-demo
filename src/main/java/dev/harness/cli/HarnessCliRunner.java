package dev.harness.cli;

import dev.harness.agent.plan.ArgumentBinding;
import dev.harness.agent.plan.ArgumentValue;
import dev.harness.agent.plan.ArgumentValueType;
import dev.harness.agent.plan.PlanNode;
import dev.harness.agent.orchestration.AgentOrchestrator;
import dev.harness.agent.orchestration.RunRequest;
import dev.harness.agent.run.RunResult;
import dev.harness.agent.trace.TraceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class HarnessCliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HarnessCliRunner.class);

    private final AgentOrchestrator orchestrator;

    public HarnessCliRunner(AgentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Override
    public void run(String... args) {
        if (args == null || args.length == 0) {
            System.out.println("Usage: ./gradlew bootRun --args=\"<goal>\"");
            return;
        }

        String goal = String.join(" ", args);
        log.info("Running CLI goal");
        RunResult result = orchestrator.run(new RunRequest(goal, "cli"));
        log.info("CLI run finished with status {}", result.status());
        print(result);
    }

    private static void print(RunResult result) {
        System.out.println("Status: " + result.status());
        if (result.report() != null && !result.report().isBlank()) {
            System.out.println();
            System.out.println("Report:");
            System.out.println(result.report());
        }
        if (result.error() != null && !result.error().isBlank()) {
            System.out.println();
            System.out.println("Error: " + result.error());
        }
        if (result.budget() != null) {
            System.out.println();
            System.out.printf("Budget pressure: %.3f%n", result.budget().pressure());
        }
        printPlan(result);
        printTrace(result);
    }

    private static void printPlan(RunResult result) {
        if (result.plan() == null || result.plan().nodes().isEmpty()) {
            return;
        }
        System.out.println();
        System.out.println("Plan:");
        for (PlanNode node : result.plan().nodes()) {
            if (node == null) {
                System.out.println("- <null node>");
                continue;
            }
            System.out.printf("- %s | tool=%s | deps=%s | status=%s%n",
                    node.getId(), node.getTool(), node.getDeps(), node.getStatus());
            if (!node.getArguments().isEmpty()) {
                System.out.println("  args: " + node.getArguments().stream()
                        .map(HarnessCliRunner::formatArgument)
                        .collect(Collectors.joining(", ")));
            }
            if (node.getError() != null && !node.getError().isBlank()) {
                System.out.println("  error: " + node.getError());
            }
        }
    }

    private static void printTrace(RunResult result) {
        System.out.println();
        System.out.println("Trace events: " + result.traceEvents().size());
        for (TraceEvent event : result.traceEvents()) {
            System.out.printf("- %s | status=%s | node=%s | role=%s | message=%s%n",
                    event.kind(), valueOrDash(event.status()), valueOrDash(event.nodeId()),
                    valueOrDash(event.role()), valueOrDash(event.message()));
            if (!event.data().isEmpty()) {
                System.out.println("  data: " + event.data());
            }
        }
    }

    private static String formatArgument(ArgumentBinding argument) {
        if (argument == null) {
            return "<null argument>";
        }
        ArgumentValue value = argument.value();
        if (value == null) {
            return argument.argumentName() + "=<null>";
        }
        if (value.type() == ArgumentValueType.NODE_RESULT) {
            return argument.argumentName() + "=NODE_RESULT(" + value.sourceNodeId() + ")";
        }
        return argument.argumentName() + "=LITERAL(" + value.literalValue() + ")";
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
