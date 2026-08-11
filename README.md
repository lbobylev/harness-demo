# Spring AI Agentic Harness

A Spring Boot 4 demo application that implements a VMAO-style agentic incident investigation harness with Spring AI 2, LangGraph4j, and OpenAI. The demo turns a natural-language incident goal into a typed agent DAG, gathers structured evidence through deterministic local agents, tests a hypothesis, builds an incident report, tracks budget, verifies the result, and emits structured trace events.

## Features

- Plans work as a typed DAG and validates it before execution.
- Applies incident-specific policy validation before execution.
- Executes independent DAG branches as dependencies become ready, with a configurable concurrency limit.
- Provides deterministic local agents for incident metrics, logs, traces, deployments, config changes, analysis, hypothesis testing, and final report assembly.
- Tracks budget pressure across tokens, agent invocations, wall-clock time, and estimated cost.
- Emits structured trace events for planning, validation, execution, verification, recovery decisions, and run completion.
- Returns diagnostic run results for planning, validation, execution, verification, and budget failures.

## Requirements

- Java 21
- OpenAI API key
- Gradle wrapper included in this repository

## Configuration

The application reads configuration from `src/main/resources/application.yml` and environment variables.

Required environment variable:

```bash
export OPENAI_API_KEY="your-api-key"
```

Optional model override:

```bash
export OPENAI_MODEL="gpt-4.1-mini"
```

Key application settings:

| Setting | Default | Description |
| --- | --- | --- |
| `spring.ai.openai.chat.options.model` | `${OPENAI_MODEL:gpt-4.1-mini}` | OpenAI chat model used by Spring AI. |
| `harness.replanning.max-replans` | `2` | Maximum number of replanning attempts after recoverable failures. |
| `harness.execution.max-concurrency` | `5` | Maximum number of DAG nodes executed in parallel. |
| `harness.verification.min-confidence` | `0.75` | Minimum report confidence before the harness asks the planner for a different investigation angle. |
| `harness.budget.max-tokens` | `20000` | Token budget for a run. |
| `harness.budget.max-agent-invocations` | `50` | Agent invocation budget for a run. |
| `harness.budget.max-wall-clock` | `60s` | Wall-clock budget for a run. |
| `harness.budget.max-estimated-cost-usd` | `0.25` | Estimated cost budget for a run. |
| `harness.budget.high-pressure-threshold` | `0.9` | Threshold for high budget pressure. |
| `harness.pricing.input-token-usd` | `0.0000004` | Estimated input-token price used for budget accounting. |
| `harness.pricing.output-token-usd` | `0.0000016` | Estimated output-token price used for budget accounting. |

## Running The CLI

Run the application with a natural-language goal:

```bash
./gradlew bootRun --args="Investigate checkout-service 5xx increase around 14:32 and identify the likely root cause"
```

The CLI prints the run status, final report when available, any error message, budget pressure, and trace event count.

If no goal is provided, the application prints usage information:

```bash
Usage: ./gradlew bootRun --args="<goal>"
```

## Testing

Run the full test suite:

```bash
./gradlew test
```

## Project Structure

```text
src/main/java/dev/harness
├── HarnessApplication.java          # Spring Boot entrypoint
├── cli/                             # Command-line runner
└── agent/
    ├── ai/                          # AI usage extraction
    ├── budget/                      # Budget limits, pricing, and pressure tracking
    ├── execution/                   # Agent response and execution errors
    ├── incident/                    # Synthetic incident data and report models
    ├── plan/                        # Plan and node models
    ├── run/                         # Run result, statuses, and recovery models
    ├── trace/                       # Structured trace events
    └── verification/                # Final report verification
└── lg4j/                            # VMAO planner, validation, execution, and agents
```

## Notes

- MetricsAgent, LogsAgent, and TracesAgent are synthetic local stubs; they do not connect to real observability backends.
- The current verifier checks that the final synthesis completed and produced a non-blank final report.
- OpenSpec artifacts for the implementation are stored under `openspec/changes/build-spring-ai-agentic-harness/`.
