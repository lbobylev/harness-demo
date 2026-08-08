package dev.harness.agent.incident;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IncidentData {

    private final List<MetricSeries> metrics = List.of(
            new MetricSeries("metric.checkout.5xx", "checkout-service", "5xx_rate", List.of(
                    point("metric.checkout.5xx.1424", "14:24", 0.002),
                    point("metric.checkout.5xx.1428", "14:28", 0.002),
                    point("metric.checkout.5xx.1431", "14:31", 0.003),
                    point("metric.checkout.5xx.1432", "14:32", 0.080),
                    point("metric.checkout.5xx.1436", "14:36", 0.083),
                    point("metric.checkout.5xx.1440", "14:40", 0.081)
            )),
            new MetricSeries("metric.checkout.latency", "checkout-service", "latency_p95", List.of(
                    point("metric.checkout.latency.1424", "14:24", 180),
                    point("metric.checkout.latency.1428", "14:28", 185),
                    point("metric.checkout.latency.1431", "14:31", 210),
                    point("metric.checkout.latency.1432", "14:32", 1450),
                    point("metric.checkout.latency.1436", "14:36", 1720),
                    point("metric.checkout.latency.1440", "14:40", 1680)
            )),
            new MetricSeries("metric.checkout.request-rate", "checkout-service", "request_rate", List.of(
                    point("metric.checkout.request-rate.1424", "14:24", 1210),
                    point("metric.checkout.request-rate.1428", "14:28", 1195),
                    point("metric.checkout.request-rate.1431", "14:31", 1208),
                    point("metric.checkout.request-rate.1432", "14:32", 1212),
                    point("metric.checkout.request-rate.1436", "14:36", 1201),
                    point("metric.checkout.request-rate.1440", "14:40", 1198)
            )),
            new MetricSeries("metric.catalog.5xx", "catalog-service", "5xx_rate", List.of(
                    point("metric.catalog.5xx.1424", "14:24", 0.001),
                    point("metric.catalog.5xx.1428", "14:28", 0.001),
                    point("metric.catalog.5xx.1431", "14:31", 0.052),
                    point("metric.catalog.5xx.1432", "14:32", 0.097),
                    point("metric.catalog.5xx.1436", "14:36", 0.112),
                    point("metric.catalog.5xx.1440", "14:40", 0.106)
            )),
            new MetricSeries("metric.catalog.latency", "catalog-service", "latency_p95", List.of(
                    point("metric.catalog.latency.1424", "14:24", 95),
                    point("metric.catalog.latency.1428", "14:28", 100),
                    point("metric.catalog.latency.1431", "14:31", 1180),
                    point("metric.catalog.latency.1432", "14:32", 2100),
                    point("metric.catalog.latency.1436", "14:36", 2360),
                    point("metric.catalog.latency.1440", "14:40", 2240)
            )),
            new MetricSeries("metric.database.latency", "database", "latency_p95", List.of(
                    point("metric.database.latency.1424", "14:24", 42),
                    point("metric.database.latency.1428", "14:28", 43),
                    point("metric.database.latency.1431", "14:31", 41),
                    point("metric.database.latency.1432", "14:32", 44),
                    point("metric.database.latency.1436", "14:36", 43),
                    point("metric.database.latency.1440", "14:40", 42)
            ))
    );

    private final List<LogEvent> logs = List.of(
            new LogEvent("log.checkout.info.1429", "14:29:18", "checkout-service", "INFO", "4.18.2",
                    "deployed checkout-service version 4.18.2 changeId=chg-1842"),
            new LogEvent("log.checkout.catalog-timeout.1", "14:32:11", "checkout-service", "ERROR", "4.18.2",
                    "catalog-service timeout after 2000ms requestId=req-1001 traceId=trc-001"),
            new LogEvent("log.checkout.catalog-timeout.2", "14:33:04", "checkout-service", "ERROR", "4.18.2",
                    "catalog-service timeout after 2000ms requestId=req-1002 traceId=trc-002"),
            new LogEvent("log.checkout.catalog-timeout.3", "14:34:22", "checkout-service", "ERROR", "4.18.2",
                    "catalog-service timeout after 2000ms requestId=req-1003 traceId=trc-003"),
            new LogEvent("log.checkout.retry.1", "14:34:23", "checkout-service", "WARN", "4.18.2",
                    "retrying catalog-service request after timeout requestId=req-1003"),
            new LogEvent("log.catalog.overload.1", "14:31:06", "catalog-service", "WARN", "2.7.5",
                    "cache refresh queue depth high workers=64"),
            new LogEvent("log.catalog.overload.2", "14:31:47", "catalog-service", "ERROR", "2.7.5",
                    "request handler saturated while loading item details duration=2200ms"),
            new LogEvent("log.catalog.overload.3", "14:32:30", "catalog-service", "ERROR", "2.7.5",
                    "request handler saturated while loading item details duration=2400ms"),
            new LogEvent("log.database.normal.1", "14:35:00", "database", "INFO", "13.4",
                    "connection pool healthy active=18 idle=42")
    );

    private final List<TraceSpan> traces = List.of(
            new TraceSpan("trace.checkout.catalog-slow.1", "trc-001", "14:32:11", "checkout-service",
                    "POST /checkout", "GET catalog-service /items", "catalog-service", 2300, "ERROR",
                    "deadline exceeded waiting for catalog-service"),
            new TraceSpan("trace.checkout.catalog-slow.2", "trc-002", "14:33:04", "checkout-service",
                    "POST /checkout", "GET catalog-service /items", "catalog-service", 2480, "ERROR",
                    "catalog-service returned 503 after timeout"),
            new TraceSpan("trace.checkout.catalog-slow.3", "trc-003", "14:34:22", "checkout-service",
                    "POST /checkout", "GET catalog-service /item-details", "catalog-service", 2210, "ERROR",
                    "deadline exceeded waiting for catalog-service"),
            new TraceSpan("trace.checkout.database-normal.1", "trc-004", "14:34:25", "checkout-service",
                    "POST /checkout", "SELECT checkout_session", "database", 38, "OK", null),
            new TraceSpan("trace.catalog.handler-slow.1", "trc-010", "14:31:47", "catalog-service",
                    "GET /items", "load item details", null, 2240, "ERROR", "worker saturation")
    );

    private final List<DeploymentEvent> deployments = List.of(
            new DeploymentEvent("deploy.checkout.4.18.2", "14:29", "checkout-service", "4.18.2", "chg-1842"),
            new DeploymentEvent("deploy.catalog.2.7.5", "10:15", "catalog-service", "2.7.5", "chg-1771")
    );

    private final List<ConfigChange> configChanges = List.of(
            new ConfigChange("config.catalog.cache-refresh", "14:30", "catalog-service",
                    "cache.refresh.parallelism", "8", "64")
    );

    public List<MetricSeries> metrics() {
        return metrics;
    }

    public List<LogEvent> logs() {
        return logs;
    }

    public List<TraceSpan> traces() {
        return traces;
    }

    public List<DeploymentEvent> deployments() {
        return deployments;
    }

    public List<ConfigChange> configChanges() {
        return configChanges;
    }

    private static MetricPoint point(String id, String timestamp, double value) {
        return new MetricPoint(id, timestamp, value);
    }
}
