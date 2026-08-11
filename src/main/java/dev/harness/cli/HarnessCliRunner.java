package dev.harness.cli;

import dev.harness.agent.orchestration.RunRequest;
import dev.harness.agent.run.RunResult;
import dev.harness.lg4j.Lg4jHarnessRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class HarnessCliRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(HarnessCliRunner.class);

    private final Lg4jHarnessRunner lg4jRunner;

    public HarnessCliRunner(Lg4jHarnessRunner lg4jRunner) {
        this.lg4jRunner = lg4jRunner;
    }

    @Override
    public void run(String... args) {
        if (args == null || args.length == 0) {
            logUsage();
            return;
        }

        String goal = String.join(" ", Arrays.copyOfRange(args, 0, args.length)).trim();
        log.info("Running CLI goal with LangGraph4j engine");
        RunRequest request = new RunRequest(goal, "cli");
        RunResult result = lg4jRunner.run(request);
        log.info("CLI run finished with status {}", result.status());
        logResult(result);
    }

    private static void logUsage() {
        log.info("Usage: ./gradlew bootRun --args=\"<goal>\"");
    }

    private static void logResult(RunResult result) {
        log.info("Status: {}", result.status());
        if (result.report() != null && !result.report().isBlank()) {
            log.info("Report:\n{}", result.report());
        }
        if (result.error() != null && !result.error().isBlank()) {
            log.info("Error: {}", result.error());
        }
        if (result.budget() != null) {
            log.info("Budget pressure: %.3f".formatted(result.budget().pressure()));
        }
        log.info("Trace events: {}", result.traceEvents().size());
    }
}
