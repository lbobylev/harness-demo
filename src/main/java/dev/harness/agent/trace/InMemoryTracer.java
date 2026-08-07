package dev.harness.agent.trace;

import java.util.ArrayList;
import java.util.List;

public class InMemoryTracer implements Tracer {

    private final List<TraceEvent> events = new ArrayList<>();

    @Override
    public synchronized void emit(TraceEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    @Override
    public synchronized List<TraceEvent> events() {
        return List.copyOf(events);
    }
}
