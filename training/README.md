# Training: TDD & Microservices with API Gateway

## Overview
Two hands-on sessions designed for experienced engineers (13-22 years) transitioning into architecture roles. The sessions build on each other: Session 1 constructs services test-first, Session 2 composes them into a production-like architecture.

## Prerequisites

### Hardware
- 8 GB RAM minimum (16 GB recommended for Docker)
- 10 GB free disk space

### Software
| Tool | Version | Purpose |
|------|---------|---------|
| Java JDK | 17+ | Service development |
| Maven | 3.9+ | Build automation |
| Docker Desktop | Latest | Container runtime |
| IDE | IntelliJ IDEA / VS Code | Development |
| curl or httpie | Latest | API testing |
| Git | Latest | Version control |

### Setup
```bash
# Clone the repository
git clone <repo-url>
cd "Microservices with API Gateway"

# Verify Java
java -version   # Should be 17+

# Verify Maven
mvn -version

# Verify Docker
docker compose version

# Pull Docker images ahead of time (saves ~5 min during lab)
docker pull postgres:16-alpine
docker pull kong:3.6
docker pull maven:3.9-eclipse-temurin-17
docker pull eclipse-temurin:17-jre-alpine
```

## Sessions

### Session 1: Test-Driven Development (2 hours)
**Focus:** TDD as an architectural tool — how writing tests first shapes API contracts, surfaces requirements, and enforces boundaries.

| Module | Duration | Type |
|--------|----------|------|
| [Module 1: TDD at the Architecture Level](session-1-tdd/module-1-tdd-architecture.md) | 25 min | Presentation + Discussion |
| [Module 2: Product Service TDD](session-1-tdd/module-2-product-service-tdd.md) | 35 min | Hands-on Lab |
| [Module 3: Inventory Service TDD](session-1-tdd/module-3-inventory-service-tdd.md) | 35 min | Hands-on Lab |
| [Module 4: Order Service TDD](session-1-tdd/module-4-order-service-tdd.md) | 20 min | Hands-on Lab |
| [Module 5: Wrap-Up](session-1-tdd/module-5-session1-wrapup.md) | 5 min | Discussion |

### Session 2: Microservices with API Gateway (2 hours)
**Focus:** Composing tested services into a running architecture with Kong, Docker, circuit breakers, and E2E testing.

| Module | Duration | Type |
|--------|----------|------|
| [Module 1: Architecture Deep Dive](session-2-microservices/module-1-architecture-deep-dive.md) | 20 min | Presentation + Discussion |
| [Module 2: Containerization](session-2-microservices/module-2-containerization.md) | 25 min | Hands-on Lab |
| [Module 3: API Gateway with Kong](session-2-microservices/module-3-api-gateway-kong.md) | 30 min | Hands-on Lab |
| [Module 4: Inter-Service Communication](session-2-microservices/module-4-inter-service-comms.md) | 25 min | Hands-on Lab |
| [Module 5: Testing & Observability](session-2-microservices/module-5-testing-observability.md) | 15 min | Hands-on Lab |
| [Module 6: Production Readiness](session-2-microservices/module-6-production-readiness.md) | 5 min | Discussion |

## Starter Templates
- `starters/session-1-starter/` — Skeleton project with failing starter tests (for Session 1)
- `starters/session-2-starter/` — Completed Session 1 code (allows joining Session 2 independently)

## Architecture
```
Client → Kong Gateway (:8000) → product-service (:8081)
                               → inventory-service (:8082)
                               → order-service (:8083)
                                        ↓
                               PostgreSQL (:5432)
                               [product_db | inventory_db | order_db]
```
