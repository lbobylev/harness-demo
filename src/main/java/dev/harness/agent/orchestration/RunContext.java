package dev.harness.agent.orchestration;

import dev.harness.agent.budget.Budget;
import dev.harness.agent.trace.Tracer;

record RunContext(String runId, String sessionId, Budget budget, Tracer tracer) {
}
