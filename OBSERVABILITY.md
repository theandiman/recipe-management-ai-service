# Observability — AI-Assisted Authoring Service

This document describes the metrics, endpoints, and alert thresholds for the
`recipe-management-ai-service` AI authoring endpoints.

---

## Metrics

All AI endpoint metrics share a common **`endpoint`** tag so dashboards and
alerts can be scoped per operation.

| Metric name                 | Type    | Tags                              | Description                                                        |
|-----------------------------|---------|-----------------------------------|--------------------------------------------------------------------|
| `ai.suggestion.requests`    | Counter | `endpoint=<value>`                | Total number of requests received by each AI endpoint.             |
| `ai.suggestion.latency`     | Timer   | `endpoint=<value>`                | End-to-end latency for each AI endpoint call (p50 / p95 / p99).   |
| `ai.suggestion.errors`      | Counter | `endpoint=<value>`                | Total number of errors/exceptions in each AI endpoint.             |
| `ai.suggestion.acceptance`  | Counter | `endpoint=suggest-fields`, `count=<n>` | Number of AI suggestions returned per request.               |

**Endpoint tag values:**

| `endpoint` value          | Service class                      |
|---------------------------|------------------------------------|
| `suggest-fields`          | `FieldSuggestionService`           |
| `refine-instructions`     | `InstructionRefinementService`     |
| `normalize-ingredients`   | `IngredientNormalizationService`   |

---

## Accessing Metrics

### Spring Boot Actuator — metrics

```
GET /actuator/metrics
GET /actuator/metrics/ai.suggestion.requests
GET /actuator/metrics/ai.suggestion.latency
GET /actuator/metrics/ai.suggestion.errors
```

Example to retrieve latency for `suggest-fields`:
```
GET /actuator/metrics/ai.suggestion.latency?tag=endpoint:suggest-fields
```

### Prometheus scrape endpoint

```
GET /actuator/prometheus
```

The Prometheus endpoint exposes all registered metrics in OpenMetrics text
format. Configure your Prometheus server to scrape this URL on the appropriate
interval (recommended: 15 s).

Example PromQL queries:

```promql
# p95 latency for suggest-fields (ms)
histogram_quantile(0.95,
  sum(rate(ai_suggestion_latency_seconds_bucket{endpoint="suggest-fields"}[5m])) by (le)
) * 1000

# Error rate per endpoint (per second)
sum(rate(ai_suggestion_errors_total[5m])) by (endpoint)

# Request throughput per endpoint
sum(rate(ai_suggestion_requests_total[5m])) by (endpoint)
```

---

## Alert Thresholds

| Alert                          | Condition                                               | Severity |
|--------------------------------|---------------------------------------------------------|----------|
| High error rate                | Error rate > 5 % of requests over a 5-minute window    | Critical |
| p95 latency regression         | p95 latency > 3 000 ms over a 5-minute window          | Warning  |
| p99 latency regression         | p99 latency > 8 000 ms over a 5-minute window          | Critical |
| Zero request throughput        | No requests received for 15 minutes during business hours | Warning |

---

## Dashboard Summary

A minimal observability dashboard should surface the following panels:

1. **Request rate** — `rate(ai_suggestion_requests_total[5m])` grouped by `endpoint`
2. **Error rate** — `rate(ai_suggestion_errors_total[5m])` grouped by `endpoint`
3. **p95 latency** — `histogram_quantile(0.95, ...)` per endpoint
4. **Suggestion acceptance** — `rate(ai_suggestion_acceptance_total[5m])` for `suggest-fields`

---

## Health Endpoint

Cloud Run readiness and liveness probes use:
```
GET /actuator/health
```

For detailed health breakdown (requires authorization):
```
GET /actuator/health/liveness
GET /actuator/health/readiness
```
