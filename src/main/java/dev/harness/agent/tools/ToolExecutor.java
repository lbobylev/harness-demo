package dev.harness.agent.tools;

import java.util.Map;

public interface ToolExecutor {

    ToolExecutionResult execute(String name, Map<String, Object> args);
}
