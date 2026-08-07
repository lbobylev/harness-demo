package dev.harness.agent.trace;

import java.util.List;

public interface Tracer {

    void emit(TraceEvent event);

    List<TraceEvent> events();
}
