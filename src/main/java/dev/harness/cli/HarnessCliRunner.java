package dev.harness.cli;

import dev.harness.agent.orchestration.AgentOrchestrator;
import dev.harness.agent.orchestration.RunRequest;
import dev.harness.agent.run.RunResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
        System.out.println("Trace events: " + result.traceEvents().size());
    }
}
