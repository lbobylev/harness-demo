package dev.harness.agent.tools;

import dev.harness.agent.run.HarnessErrorCode;

public class ToolExecutionException extends RuntimeException {

    private final HarnessErrorCode errorCode;

    public ToolExecutionException(String message) {
        this(HarnessErrorCode.TOOL_EXECUTION_FAILED, message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        this(HarnessErrorCode.TOOL_EXECUTION_FAILED, message, cause);
    }

    public ToolExecutionException(HarnessErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode == null ? HarnessErrorCode.TOOL_EXECUTION_FAILED : errorCode;
    }

    public ToolExecutionException(HarnessErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode == null ? HarnessErrorCode.TOOL_EXECUTION_FAILED : errorCode;
    }

    public HarnessErrorCode errorCode() {
        return errorCode;
    }
}
