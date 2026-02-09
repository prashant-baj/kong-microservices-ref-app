# Microservices with API Gateway — Training Lab

An Order Management Platform demonstrating TDD practices and microservices architecture with Kong API Gateway.

## Architecture

```
                        ┌──────────────┐
                        │    Client    │
                        └──────┬───────┘
                               │
                        ┌──────▼───────┐
                        │  Kong API    │
                        │  Gateway     │
                        │  :8000       │
                        └──┬───┬───┬───┘
                           │   │   │
              ┌────────────┘   │   └────────────┐
              │                │                │
     ┌────────▼──────┐ ┌──────▼────────┐ ┌─────▼─────────┐
     │   Product     │ │  Inventory    │ │    Order       │
     │   Service     │ │  Service      │ │    Service     │
     │   :8081       │ │  :8082        │ │    :8083       │
     └────────┬──────┘ └──────┬────────┘ └─────┬─────────┘
              │               │                │
     ┌────────▼──────┐ ┌──────▼────────┐ ┌─────▼─────────┐
     │  product_db   │ │ inventory_db  │ │   order_db    │
     └───────────────┘ └───────────────┘ └───────────────┘
              └───────────────┼───────────────┘
                       ┌──────▼───────┐
                       │  PostgreSQL  │
                       │  :5432       │
                       └──────────────┘
```

## Services

| Service | Port | Description |
|---------|------|-------------|
| **product-service** | 8081 | Product catalog CRUD |
| **inventory-service** | 8082 | Stock management and reservation |
| **order-service** | 8083 | Order lifecycle, orchestrates product + inventory |
| **Kong Gateway** | 8000 | API routing, rate limiting, auth, CORS |
| **PostgreSQL** | 5432 | Shared instance, per-service databases |

## Prerequisites

- Java 17+
- Maven 3.9+
- Docker & Docker Compose
- (Optional) curl or httpie for API testing

## Quick Start

```bash
# Build all services
mvn clean package -DskipTests

# Start everything
docker compose up --build

# Test through the gateway
curl http://localhost:8000/api/products
```

## Training Sessions

This lab supports two training sessions. See `training/README.md` for details.

1. **Session 1: Test-Driven Development** — Build services test-first
2. **Session 2: Microservices with API Gateway** — Compose services with Kong and Docker
