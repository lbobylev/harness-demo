package dev.harness.agent.execution;

import dev.harness.agent.run.HarnessErrorCode;

public class AgentExecutionException extends RuntimeException {

    private final HarnessErrorCode errorCode;

    public AgentExecutionException(String message) {
        this(HarnessErrorCode.AGENT_EXECUTION_FAILED, message);
    }

    public AgentExecutionException(String message, Throwable cause) {
        this(HarnessErrorCode.AGENT_EXECUTION_FAILED, message, cause);
    }

    public AgentExecutionException(HarnessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? HarnessErrorCode.AGENT_EXECUTION_FAILED : errorCode;
    }

    public AgentExecutionException(HarnessErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? HarnessErrorCode.AGENT_EXECUTION_FAILED : errorCode;
    }

    public HarnessErrorCode errorCode() {
        return errorCode;
    }
}
