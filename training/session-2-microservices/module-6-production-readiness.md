# Module 6: Architecture Review and Production Readiness

---

## What We Built Today

Across both sessions, we constructed a complete microservices architecture:

```
Client → Kong (:8000) ──→ product-service (:8081) → product_db
                     ├──→ inventory-service (:8082) → inventory_db
                     └──→ order-service (:8083) → order_db
                              ├── calls → product-service
                              └── calls → inventory-service (with circuit breaker)
```

**Session 1:** Built services test-first (TDD) — 27+ tests across 3 services
**Session 2:** Composed them into a running architecture with Docker, Kong, resilience, and E2E tests

---

## Design Decisions Made (and Why)

| Decision | Choice | Alternative | Why |
|----------|--------|-------------|-----|
| Communication | Synchronous REST | Event-driven (Kafka) | Simpler for lab; discuss async trade-offs |
| API Gateway | Kong (DB-less) | Spring Cloud Gateway | Language-agnostic, production-grade, GitOps |
| Database | Per-service (single Postgres, 3 DBs) | Shared database | Enforces bounded contexts |
| Schema management | Flyway | Hibernate auto-DDL | Explicit, versioned, production-safe |
| Locking | Optimistic (@Version) | Pessimistic (SELECT FOR UPDATE) | Better throughput for low-contention |
| HTTP client | RestClient | WebClient, RestTemplate | Modern, synchronous, Spring Boot 3.2+ |
| Testing DB | Testcontainers (real Postgres) | H2 in-memory | Production parity |
| Resilience | Resilience4j circuit breaker | Hystrix, manual retry | Active project, annotation-based |

---

## What Would Change for Production

| Lab Approach | Production Approach | Why |
|-------------|---------------------|-----|
| Docker Compose | Kubernetes | Scaling, rolling updates, self-healing |
| Single Postgres container | Managed DB per service (RDS) | Isolation, backups, scaling |
| Kong in Docker | Kong Ingress Controller or API Gateway service | K8s native routing |
| `application-docker.yml` | Centralized config (Spring Cloud Config, Vault) | Secret management, dynamic config |
| Correlation ID filter | OpenTelemetry + Jaeger/Zipkin | Full distributed tracing |
| Console logging | ELK stack or Grafana Loki | Aggregated, searchable logs |
| No metrics | Micrometer + Prometheus + Grafana | Dashboards, alerting |
| Manual Dockerfiles | CI/CD pipeline (GitHub Actions, Jenkins) | Automated build/test/deploy |
| No service mesh | Istio or Linkerd | mTLS, traffic management, observability |

---

## When NOT to Use Microservices

This architecture is powerful but complex. Before adopting it, consider:

- **Start with a modular monolith** — Same bounded contexts, same code separation, but one deployable. Extract to microservices when a specific module needs independent scaling or deployment.
- **Team size matters** — Microservices work when each service has a dedicated team. If one team owns all three services, the overhead isn't justified.
- **Distributed systems are hard** — Network partitions, eventual consistency, distributed debugging, data integrity across services. Every inter-service call is a potential failure point.
- **Consider serverless** — If your services are small and event-driven, AWS Lambda or similar might be simpler than containers.

---

## Architecture Decision Record Template

For your own projects, document decisions using ADRs:

```markdown
# ADR-001: Use Kong as API Gateway

## Status: Accepted

## Context
We need an API gateway for routing, rate limiting, and authentication.

## Decision
Use Kong in DB-less (declarative) mode.

## Consequences
- (+) Language-agnostic — works with any backend
- (+) Declarative config in version control
- (+) Rich plugin ecosystem
- (-) Separate infrastructure component to manage
- (-) Team needs to learn Kong configuration
```

---

## Key Takeaways

1. **Microservices are a team scaling strategy** — not a technical one. The primary benefit is independent deployment by autonomous teams.
2. **The gateway is your API** — Kong's route configuration defines the public contract. Design it carefully.
3. **Design for failure** — Circuit breakers, compensation, idempotency. Every inter-service call can fail.
4. **Test at the right level** — Unit tests for logic, integration tests for persistence, E2E tests for critical paths.
5. **Start simple, evolve** — Docker Compose → Kubernetes. REST → Events. Monolith → Microservices. Evolve when complexity justifies the cost.

---

## Further Reading
- *Building Microservices* (2nd ed.) — Sam Newman
- *Designing Data-Intensive Applications* — Martin Kleppmann
- *Release It!* — Michael Nygard (resilience patterns)
- *Fundamentals of Software Architecture* — Richards & Ford
- Kong documentation: https://docs.konghq.com
- Resilience4j documentation: https://resilience4j.readme.io
