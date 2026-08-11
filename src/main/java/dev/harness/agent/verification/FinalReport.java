package dev.harness.agent.verification;

import java.util.List;

public interface FinalReport {

    String rootCause();

    double confidence();

    List<String> timeline();

    List<String> evidence();

    List<String> rejectedHypotheses();

    String recommendedAction();

    String reportText();
}
