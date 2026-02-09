# Session 2: Microservices with API Gateway

## Duration
2 hours (includes hands-on exercises and architectural discussions)

## Learning Objectives

By the end of this session, participants will be able to:

- **Decompose a modular monolith into independently deployable microservices** and articulate the trade-offs involved in that decision
- **Design and configure an API Gateway** (Kong) as the single entry point for a microservices architecture, applying cross-cutting concerns as gateway plugins
- **Containerize Spring Boot services** using multi-stage Docker builds with production-appropriate JVM tuning and health checks
- **Implement synchronous inter-service communication** with RestClient and apply resilience patterns (circuit breakers, timeouts, fallbacks) to handle partial failures gracefully
- **Apply the database-per-service pattern** to enforce bounded context isolation at the data layer
- **Evaluate architectural trade-offs** across gateway placement, communication style, failure handling, and observability using Architecture Decision Records (ADRs)

## Target Audience

Experienced developers (13-22 years) transitioning into architect roles. This session assumes strong Java/Spring proficiency and focuses on the *why* behind architectural decisions, not introductory Docker or REST concepts.

## Prerequisites

- **Session 1 complete** (Product, Inventory, and Order services built and tested locally) -- OR -- use the `session-2-starter` branch which provides a working baseline
- **Docker Desktop** installed and running (verify with `docker info`)
- **At least 8 GB RAM** allocated to Docker (Settings > Resources) -- the full stack runs 5 containers
- **Ports available:** 8000 (Kong proxy), 8001 (Kong admin), 5432 (PostgreSQL), 8081-8083 (services)
- Familiarity with `docker compose` CLI (v2 syntax)

## Session Flow

| Order | Module | Duration | Type |
|-------|--------|----------|------|
| 1 | [Architecture Deep Dive](module-1-architecture-deep-dive.md) | 20 min | Presentation + Discussion |
| 2 | [Containerization Exercise](module-2-containerization.md) | 25 min | Hands-on |
| 3 | [API Gateway with Kong](module-3-api-gateway-kong.md) | 30 min | Hands-on |
| 4 | [Inter-Service Communication](module-4-inter-service-comms.md) | 25 min | Hands-on |
| 5 | [E2E Testing and Observability](module-5-testing-observability.md) | 15 min | Hands-on |
| 6 | [Production Readiness Review](module-6-production-readiness.md) | 5 min | Discussion |

## Architecture at a Glance

At the end of this session, participants will have built and tested the following architecture:

```
                         +------------------+
                         |    Client/curl   |
                         +--------+---------+
                                  |
                                  | :8000
                         +--------v---------+
                         |    Kong Gateway   |
                         |  (DB-less mode)   |
                         |  - Rate limiting  |
                         |  - Auth (key-auth)|
                         |  - CORS           |
                         |  - Logging        |
                         +---+-----+-----+--+
                             |     |     |
               +-------------+     |     +--------------+
               |                   |                    |
      +--------v-------+  +-------v--------+  +--------v-------+
      | product-service |  | inventory-svc  |  |  order-service  |
      |     :8081       |  |     :8082      |  |      :8083      |
      +--------+--------+  +-------+--------+  +--+----------+--+
               |                   |               |          |
               v                   v               v          v
        +-----------+       +-----------+    (calls product  (calls
        | product_db|       |inventory_db|    & inventory    inventory
        +-----------+       +-----------+     via REST)      via REST)
                                              +----------+
                                              | order_db |
                                              +----------+

        All databases run in a single PostgreSQL instance
        with logical separation (database-per-service pattern)
```

## What You Will Build

1. **Multi-stage Dockerfiles** for each Spring Boot service
2. **Docker Compose stack** with PostgreSQL (3 databases), 3 services, and Kong
3. **Kong declarative configuration** with routing, rate limiting, authentication, and CORS
4. **Inter-service communication** via RestClient with circuit breaker resilience
5. **End-to-end integration tests** running against the gateway endpoint

## Quick Start (if using session-2-starter)

```bash
git checkout session-2-starter
docker compose up --build -d
# Wait for health checks to pass (~30 seconds)
docker compose ps
```

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---------|-------------|-----|
| Port 5432 in use | Local PostgreSQL running | Stop local PG or change compose port mapping |
| Services fail to start | Database not ready | Check `depends_on` with health check conditions |
| Kong returns 503 | Upstream service not healthy | `docker compose logs <service>` to check startup |
| Out of memory | Docker RAM too low | Increase Docker Desktop memory allocation to 8 GB |
| Build takes forever | No Docker layer cache | Run `docker compose build` once, then use `up` |
