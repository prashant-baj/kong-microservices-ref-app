# Module 5: Session 1 Wrap-Up

## Duration
5 minutes

---

## What We Built

Through strict Red-Green-Refactor, we constructed three microservices:

| Service | Tests | Key Patterns Driven by TDD |
|---------|-------|----------------------------|
| **product-service** | 8 tests | API contracts, validation, error handling, DTO separation |
| **inventory-service** | 9 tests | Business rules, idempotency, optimistic locking, Testcontainers |
| **order-service** | 10 tests | Inter-service orchestration, compensation logic, client mocking |

Total: **~27 tests** across 3 services, all written before their corresponding implementation.

---

## What TDD Proved

1. **API contracts are explicit** — MockMvc tests document every endpoint's request/response shape
2. **Business rules are captured** — Insufficient stock, duplicate products, idempotent reservations
3. **Error paths are handled** — 400, 404, 409 responses all have tests
4. **Compensation logic works** — Failed reservations trigger rollback of successful ones
5. **Data integrity is verified** — Optimistic locking prevents concurrent overselling

---

## What Is NOT Tested Yet

This is the bridge to Session 2:

- **Inter-service contracts** — Our mocks assume product-service returns `{id, name, price}`. What if it doesn't?
- **Gateway routing** — Does Kong correctly route `/api/products` to product-service?
- **End-to-end flows** — Can we actually create a product → add stock → place an order through the gateway?
- **Resilience** — What happens when inventory-service is down? Does the circuit breaker work?
- **Infrastructure** — Do the Docker containers start? Do Flyway migrations run? Do health checks pass?

> **Session 2 Preview:** We have well-tested services. Now we compose them into an architecture — with Docker, Kong, circuit breakers, and E2E integration tests.

---

## Key Takeaways

1. **TDD is design** — Tests drove our API contracts, exception hierarchy, and idempotency patterns
2. **The test pyramid has a middle** — Integration tests with Testcontainers catch what unit tests miss
3. **Compensation over transactions** — TDD surfaced the need for rollback logic naturally
4. **Mock boundaries carefully** — Service client mocks are fast but fragile without contract tests
5. **Know when to stop** — We didn't TDD DTOs, configs, or migrations — TDD the logic, not the ceremony

---

## Further Reading
- *Growing Object-Oriented Software, Guided by Tests* — Freeman & Pryce (the canonical TDD architecture book)
- *Testing Microservices* — Toby Clemson (Martin Fowler's blog)
- *Consumer-Driven Contracts* — Pact documentation
- *Test Pyramid* — Ham Vocke (Martin Fowler's blog)
