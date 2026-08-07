package dev.harness.agent.trace;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTracerTests {

    @Test
    void storesEmittedEventsInOrder() {
        InMemoryTracer tracer = new InMemoryTracer();

        tracer.emit(TraceEvent.of("run-1", "run_started", "orchestrator", null));
        tracer.emit(TraceEvent.of("run-1", "run_finished", "orchestrator", null));

        assertThat(tracer.events())
                .extracting(TraceEvent::kind)
                .containsExactly("run_started", "run_finished");
    }
}
