# Spring AI Agentic Harness

A Spring Boot 4 demo application that implements an agentic harness with Spring AI 2 and OpenAI. The demo domain recommends computer games by combining local genre facts, review summaries, a local game catalog, fake price data, LLM-backed planning, controlled tool execution, budget tracking, verification, and structured tracing.

## Features

- Plans work as a typed DAG and validates it before execution.
- Executes independent DAG nodes in parallel with a configurable concurrency limit.
- Provides deterministic local tools for genre facts, genre reviews, game catalog data, and fake game prices.
- Uses an LLM-backed summarizer for the final game recommendation report.
- Tracks budget pressure across tokens, tool calls, wall-clock time, and estimated cost.
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
| `harness.replanning.max-replans` | `1` | Maximum number of replanning attempts after recoverable failures. |
| `harness.execution.max-concurrency` | `5` | Maximum number of DAG nodes executed in parallel. |
| `harness.budget.max-tokens` | `20000` | Token budget for a run. |
| `harness.budget.max-tool-calls` | `50` | Tool-call budget for a run. |
| `harness.budget.max-wall-clock` | `60s` | Wall-clock budget for a run. |
| `harness.budget.max-estimated-cost-usd` | `0.25` | Estimated cost budget for a run. |
| `harness.budget.high-pressure-threshold` | `0.9` | Threshold for high budget pressure. |
| `harness.pricing.input-token-usd` | `0.0000004` | Estimated input-token price used for budget accounting. |
| `harness.pricing.output-token-usd` | `0.0000016` | Estimated output-token price used for budget accounting. |

## Running The CLI

Run the application with a natural-language goal:

```bash
./gradlew bootRun --args="Recommend computer games for someone who likes strategy, exploration, and story-rich RPGs"
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
    ├── execution/                   # DAG execution
    ├── orchestration/               # End-to-end run orchestration
    ├── plan/                        # Plan and node models
    ├── planning/                    # Spring AI planner integration
    ├── run/                         # Run result, statuses, and recovery models
    ├── tools/                       # Game recommendation tools and tool catalog
    ├── trace/                       # Structured trace events
    ├── validation/                  # DAG validation
    └── verification/                # Final report verification
```

## Notes

- Final prices are expected to come from deterministic local fake price data, not from model-generated values.
- The current verifier checks that the final synthesis completed and produced a non-blank final report.
- OpenSpec artifacts for the implementation are stored under `openspec/changes/build-spring-ai-agentic-harness/`.
